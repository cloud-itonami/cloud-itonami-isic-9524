(ns furniture.store
  "SSoT for the furniture-and-home-furnishings-repair actor, behind a
  `Store` protocol so the backend is a swap, not a rewrite -- the same
  seam every prior `cloud-itonami-isic-*` actor in this fleet uses,
  closely modeled on `cloud-itonami-isic-9522`'s `applianceshop.
  store`:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/furniture/store_contract_test.clj), which is the whole point:
  the actor, the Repair Shop Governor and the audit ledger never know
  which SSoT they run on.

  Like `repairshop.store`'s/`commrepair.store`'s/`applianceshop.
  store`'s own dual history, this actor has TWO actuation events
  (repair completion, item return) acting on the SAME entity (a
  repair ticket), each with its OWN history collection, sequence
  counter and dedicated double-actuation-guard boolean (`:repair-
  completed?`/`:item-returned?`, never a `:status` value).

  Beyond `applianceshop.store`'s own `refrigerant-screening-of`, this
  store carries `flammability-screening-of` (furniture-flammability-
  compliance confirmation status) -- the genuinely new concern this
  vertical adds, since reupholstery/filling-material-replacement work
  (one of this blueprint's own core activities) is a real, distinct
  regulatory concern beyond `repairshop`/9521's, `commrepair`/9512's
  and `applianceshop`/9522's own catalogs.

  The ledger stays append-only on every backend: 'which ticket was
  screened for a failed post-repair safety test or an unconfirmed
  flammability-compliance status, which repair was completed, which
  item was returned, on what jurisdictional basis, approved by whom'
  is always a query over an immutable log -- the audit trail a
  customer trusting a repair shop needs, and the evidence an operator
  needs if a repair or a return is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [furniture.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (ticket [s id])
  (all-tickets [s])
  (safety-screening-of [s ticket-id] "committed post-repair safety-test screening verdict for a ticket, or nil")
  (flammability-screening-of [s ticket-id] "committed flammability-compliance screening verdict for a ticket, or nil")
  (assessment-of [s ticket-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (completion-history [s] "the append-only repair-completion history (furniture.registry drafts)")
  (return-history [s] "the append-only item-return history (furniture.registry drafts)")
  (next-completion-sequence [s jurisdiction] "next repair-completion-number sequence for a jurisdiction")
  (next-return-sequence [s jurisdiction] "next item-return-number sequence for a jurisdiction")
  (ticket-already-completed? [s ticket-id] "has this ticket's repair already been completed?")
  (ticket-already-returned? [s ticket-id] "has this ticket's item already been returned?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-tickets [s tickets] "replace/seed the ticket directory (map id->ticket)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained ticket set covering both actuation
  lifecycles (repair completion, item return) plus the two
  unconditional-eval screening findings, so the actor + tests run
  offline."
  []
  {:tickets
   {"ticket-1" {:id "ticket-1" :customer "Sakura Tanaka" :item "Wooden dining chair (leg reglued)" :item-type :chair
                 :parts-quantity 1 :parts-unit-price 15 :claimed-parts-cost 15.0
                 :safety-test-passed? true
                 :involves-upholstery-work? false :flammability-compliance-confirmed? false
                 :repair-completed? false :item-returned? false
                 :jurisdiction "JPN" :status :intake}
    "ticket-2" {:id "ticket-2" :customer "Atlantis Doe" :item "Bookshelf (shelf-pin repair)" :item-type :bookshelf
                 :parts-quantity 1 :parts-unit-price 10 :claimed-parts-cost 10.0
                 :safety-test-passed? true
                 :involves-upholstery-work? false :flammability-compliance-confirmed? false
                 :repair-completed? false :item-returned? false
                 :jurisdiction "ATL" :status :intake}
    "ticket-3" {:id "ticket-3" :customer "鈴木一郎" :item "Sofa (frame repair)" :item-type :sofa
                 :parts-quantity 1 :parts-unit-price 80 :claimed-parts-cost 120.0
                 :safety-test-passed? true
                 :involves-upholstery-work? false :flammability-compliance-confirmed? false
                 :repair-completed? false :item-returned? false
                 :jurisdiction "JPN" :status :intake}
    "ticket-4" {:id "ticket-4" :customer "田中花子" :item "Recliner (reclining-mechanism repair)" :item-type :recliner
                 :parts-quantity 1 :parts-unit-price 40 :claimed-parts-cost 40.0
                 :safety-test-passed? false
                 :involves-upholstery-work? false :flammability-compliance-confirmed? false
                 :repair-completed? false :item-returned? false
                 :jurisdiction "JPN" :status :intake}
    "ticket-5" {:id "ticket-5" :customer "佐藤次郎" :item "Sofa (reupholstered)" :item-type :sofa
                 :parts-quantity 1 :parts-unit-price 200 :claimed-parts-cost 200.0
                 :safety-test-passed? true
                 :involves-upholstery-work? true :flammability-compliance-confirmed? false
                 :repair-completed? false :item-returned? false
                 :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- complete-repair!
  "Backend-agnostic `:ticket/mark-completed` -- looks up the ticket via
  the protocol and drafts the repair-completion record, and returns
  {:result .. :ticket-patch ..} for the caller to persist."
  [s ticket-id]
  (let [t (ticket s ticket-id)
        seq-n (next-completion-sequence s (:jurisdiction t))
        result (registry/register-repair-completion ticket-id (:jurisdiction t) seq-n)]
    {:result result
     :ticket-patch {:repair-completed? true
                    :completion-number (get result "completion_number")}}))

(defn- return-item!
  "Backend-agnostic `:ticket/mark-returned` -- looks up the ticket via
  the protocol and drafts the item-return record, and returns
  {:result .. :ticket-patch ..} for the caller to persist."
  [s ticket-id]
  (let [t (ticket s ticket-id)
        seq-n (next-return-sequence s (:jurisdiction t))
        result (registry/register-item-return ticket-id (:jurisdiction t) seq-n)]
    {:result result
     :ticket-patch {:item-returned? true
                    :return-number (get result "return_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (ticket [_ id] (get-in @a [:tickets id]))
  (all-tickets [_] (sort-by :id (vals (:tickets @a))))
  (safety-screening-of [_ id] (get-in @a [:safety-screenings id]))
  (flammability-screening-of [_ id] (get-in @a [:flammability-screenings id]))
  (assessment-of [_ ticket-id] (get-in @a [:assessments ticket-id]))
  (ledger [_] (:ledger @a))
  (completion-history [_] (:completions @a))
  (return-history [_] (:returns @a))
  (next-completion-sequence [_ jurisdiction] (get-in @a [:completion-sequences jurisdiction] 0))
  (next-return-sequence [_ jurisdiction] (get-in @a [:return-sequences jurisdiction] 0))
  (ticket-already-completed? [_ ticket-id] (boolean (get-in @a [:tickets ticket-id :repair-completed?])))
  (ticket-already-returned? [_ ticket-id] (boolean (get-in @a [:tickets ticket-id :item-returned?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :ticket/upsert
      (swap! a update-in [:tickets (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :safety-screening/set
      (swap! a assoc-in [:safety-screenings (first path)] payload)

      :flammability-screening/set
      (swap! a assoc-in [:flammability-screenings (first path)] payload)

      :ticket/mark-completed
      (let [ticket-id (first path)
            {:keys [result ticket-patch]} (complete-repair! s ticket-id)
            jurisdiction (:jurisdiction (ticket s ticket-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:completion-sequences jurisdiction] (fnil inc 0))
                       (update-in [:tickets ticket-id] merge ticket-patch)
                       (update :completions registry/append result))))
        result)

      :ticket/mark-returned
      (let [ticket-id (first path)
            {:keys [result ticket-patch]} (return-item! s ticket-id)
            jurisdiction (:jurisdiction (ticket s ticket-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:return-sequences jurisdiction] (fnil inc 0))
                       (update-in [:tickets ticket-id] merge ticket-patch)
                       (update :returns registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-tickets [s tickets] (when (seq tickets) (swap! a assoc :tickets tickets)) s))

(defn seed-db
  "A MemStore seeded with the demo ticket set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :safety-screenings {} :flammability-screenings {}
                           :ledger [] :completion-sequences {} :completions []
                           :return-sequences {} :returns []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/safety-screening/flammability-
  screening payloads, ledger facts, completion/return records) are
  stored as EDN strings so `langchain.db` doesn't expand them into
  sub-entities -- the same convention every sibling actor's store
  uses."
  {:ticket/id                        {:db/unique :db.unique/identity}
   :assessment/ticket-id             {:db/unique :db.unique/identity}
   :safety-screening/ticket-id       {:db/unique :db.unique/identity}
   :flammability-screening/ticket-id {:db/unique :db.unique/identity}
   :ledger/seq                       {:db/unique :db.unique/identity}
   :completion/seq                   {:db/unique :db.unique/identity}
   :return/seq                       {:db/unique :db.unique/identity}
   :completion-sequence/jurisdiction {:db/unique :db.unique/identity}
   :return-sequence/jurisdiction     {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- ticket->tx [{:keys [id customer item item-type parts-quantity parts-unit-price claimed-parts-cost
                          safety-test-passed? involves-upholstery-work? flammability-compliance-confirmed?
                          repair-completed? item-returned?
                          jurisdiction status completion-number return-number]}]
  (cond-> {:ticket/id id}
    customer                                       (assoc :ticket/customer customer)
    item                                             (assoc :ticket/item item)
    item-type                                          (assoc :ticket/item-type item-type)
    parts-quantity                                       (assoc :ticket/parts-quantity parts-quantity)
    parts-unit-price                                       (assoc :ticket/parts-unit-price parts-unit-price)
    claimed-parts-cost                                       (assoc :ticket/claimed-parts-cost claimed-parts-cost)
    (some? safety-test-passed?)                                (assoc :ticket/safety-test-passed? safety-test-passed?)
    (some? involves-upholstery-work?)                            (assoc :ticket/involves-upholstery-work? involves-upholstery-work?)
    (some? flammability-compliance-confirmed?)                     (assoc :ticket/flammability-compliance-confirmed? flammability-compliance-confirmed?)
    (some? repair-completed?)                                        (assoc :ticket/repair-completed? repair-completed?)
    (some? item-returned?)                                             (assoc :ticket/item-returned? item-returned?)
    jurisdiction                                                         (assoc :ticket/jurisdiction jurisdiction)
    status                                                                 (assoc :ticket/status status)
    completion-number                                                        (assoc :ticket/completion-number completion-number)
    return-number                                                              (assoc :ticket/return-number return-number)))

(def ^:private ticket-pull
  [:ticket/id :ticket/customer :ticket/item :ticket/item-type :ticket/parts-quantity
   :ticket/parts-unit-price :ticket/claimed-parts-cost :ticket/safety-test-passed?
   :ticket/involves-upholstery-work? :ticket/flammability-compliance-confirmed?
   :ticket/repair-completed? :ticket/item-returned?
   :ticket/jurisdiction :ticket/status :ticket/completion-number :ticket/return-number])

(defn- pull->ticket [m]
  (when (:ticket/id m)
    {:id (:ticket/id m) :customer (:ticket/customer m) :item (:ticket/item m)
     :item-type (:ticket/item-type m) :parts-quantity (:ticket/parts-quantity m)
     :parts-unit-price (:ticket/parts-unit-price m) :claimed-parts-cost (:ticket/claimed-parts-cost m)
     :safety-test-passed? (boolean (:ticket/safety-test-passed? m))
     :involves-upholstery-work? (boolean (:ticket/involves-upholstery-work? m))
     :flammability-compliance-confirmed? (boolean (:ticket/flammability-compliance-confirmed? m))
     :repair-completed? (boolean (:ticket/repair-completed? m))
     :item-returned? (boolean (:ticket/item-returned? m))
     :jurisdiction (:ticket/jurisdiction m) :status (:ticket/status m)
     :completion-number (:ticket/completion-number m) :return-number (:ticket/return-number m)}))

(defrecord DatomicStore [conn]
  Store
  (ticket [_ id]
    (pull->ticket (d/pull (d/db conn) ticket-pull [:ticket/id id])))
  (all-tickets [_]
    (->> (d/q '[:find [?id ...] :where [?e :ticket/id ?id]] (d/db conn))
         (map #(pull->ticket (d/pull (d/db conn) ticket-pull [:ticket/id %])))
         (sort-by :id)))
  (safety-screening-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?tid
                :where [?k :safety-screening/ticket-id ?tid] [?k :safety-screening/payload ?p]]
              (d/db conn) id)))
  (flammability-screening-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?tid
                :where [?k :flammability-screening/ticket-id ?tid] [?k :flammability-screening/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ ticket-id]
    (dec* (d/q '[:find ?p . :in $ ?tid
                :where [?a :assessment/ticket-id ?tid] [?a :assessment/payload ?p]]
              (d/db conn) ticket-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (completion-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :completion/seq ?s] [?e :completion/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (return-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :return/seq ?s] [?e :return/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-completion-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :completion-sequence/jurisdiction ?j] [?e :completion-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-return-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :return-sequence/jurisdiction ?j] [?e :return-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (ticket-already-completed? [s ticket-id]
    (boolean (:repair-completed? (ticket s ticket-id))))
  (ticket-already-returned? [s ticket-id]
    (boolean (:item-returned? (ticket s ticket-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :ticket/upsert
      (d/transact! conn [(ticket->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/ticket-id (first path) :assessment/payload (enc payload)}])

      :safety-screening/set
      (d/transact! conn [{:safety-screening/ticket-id (first path) :safety-screening/payload (enc payload)}])

      :flammability-screening/set
      (d/transact! conn [{:flammability-screening/ticket-id (first path) :flammability-screening/payload (enc payload)}])

      :ticket/mark-completed
      (let [ticket-id (first path)
            {:keys [result ticket-patch]} (complete-repair! s ticket-id)
            jurisdiction (:jurisdiction (ticket s ticket-id))
            next-n (inc (next-completion-sequence s jurisdiction))]
        (d/transact! conn
                     [(ticket->tx (assoc ticket-patch :id ticket-id))
                      {:completion-sequence/jurisdiction jurisdiction :completion-sequence/next next-n}
                      {:completion/seq (count (completion-history s)) :completion/record (enc (get result "record"))}])
        result)

      :ticket/mark-returned
      (let [ticket-id (first path)
            {:keys [result ticket-patch]} (return-item! s ticket-id)
            jurisdiction (:jurisdiction (ticket s ticket-id))
            next-n (inc (next-return-sequence s jurisdiction))]
        (d/transact! conn
                     [(ticket->tx (assoc ticket-patch :id ticket-id))
                      {:return-sequence/jurisdiction jurisdiction :return-sequence/next next-n}
                      {:return/seq (count (return-history s)) :return/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-tickets [s tickets]
    (when (seq tickets) (d/transact! conn (mapv ticket->tx (vals tickets)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:tickets ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [tickets]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-tickets s tickets))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo ticket set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
