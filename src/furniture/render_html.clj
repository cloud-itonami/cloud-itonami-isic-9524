(ns furniture.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2: this repo previously had NO demo
  page and no generator at all. This namespace drives the REAL actor
  stack (`furniture.operation` -> `furniture.governor` ->
  `furniture.store`, via langgraph `g/run*` exactly as
  `furniture.sim` does) through a scenario adapted from this repo's own
  `furniture.sim` demo driver (`clojure -M:dev:run`, confirmed BEFORE
  writing this file to produce a sensible ledger against the real
  seeded ticket ids `ticket-1`..`ticket-5`), and renders the resulting
  store deterministically -- no invented numbers, no timestamps in the
  page content, byte-identical across reruns against the same seed
  (verify by diffing two consecutive runs into scratch dirs).

  PROVENANCE DISCIPLINE. Every entity, identifier and number on the
  emitted page traces to something this repo actually holds:

    - ticket ids, customers, item descriptions, parts quantities/unit
      prices/claimed costs, `:involves-upholstery-work?` and
      `:flammability-compliance-confirmed?` come from
      `furniture.store/demo-data` -- the DECLARED SEED. In particular
      the free-text `:item` string (e.g. \"Sofa (reupholstered)\") and
      the upholstery flag are seed declarations, NOT lookups: this repo
      has no material/wood-species/textile registry, and the page says
      so rather than implying a taxonomy that does not exist.
    - jurisdiction authorities, legal bases, evidence checklists and
      the flammability sub-citation come from `furniture.facts/catalog`
      -- including the honest ABSENCE of a flammability regime for JPN.
    - recomputed parts costs come from
      `furniture.registry/compute-parts-cost`.
    - completion/return record ids come from
      `furniture.registry/register-*` via the store.
    - holds, rules, details, confidences and dispositions come from the
      `furniture.governor` verdicts that this run actually produced.

  Only the action-gate table is hand-described, and it describes fixed
  contract (`furniture.phase`'s `:writes`/`:auto` sets) rather than
  runtime telemetry -- and even there the phase-3 auto set is read out
  of `furniture.phase/phases` at render time so the table cannot drift
  from the code.

  BUILD-TIME INVARIANT. `-main` REFUSES to write the console if the run
  produced zero `:governor-hold` facts (precedent:
  `cloud-itonami-isic-2513`). A console that shows no real hold would be
  a page about a governor that never governed, and the failure mode this
  guards is silent: a scenario edit that stops exercising the governor
  would otherwise still emit a plausible-looking page.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [furniture.facts :as facts]
            [furniture.phase :as phase]
            [furniture.registry :as registry]
            [furniture.store :as store]
            [furniture.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :repair-technician :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, and returns `{:db .. :runs [..]}` -- the store
  plus every langgraph run result, so the renderer can derive approver
  attribution from what the run actually produced rather than assuming
  it.

  ticket-1 clears a full lifecycle: intake (auto-commits clean at phase
  3 -- the only op in any phase's `:auto` set), a jurisdiction
  assessment (phase-gated, approved), a post-repair safety screening
  (approved), a flammability-compliance screening (approved -- this
  ticket does no upholstery work, so the requirement does not arise at
  all), a repair completion and an item return (both ALWAYS escalate:
  `:actuation/complete-repair`/`:actuation/return-item` are permanently
  high-stakes, never auto at any phase -- both approved).

  Then six DISTINCT HARD holds, none of which ever reaches a human:
    ticket-2  `:no-spec-basis`                     -- assessment for a jurisdiction
                                                     absent from `furniture.facts`
    ticket-3  `:parts-cost-mismatch`               -- claimed 120.0 vs the
                                                     independently recomputed 1 x 80
    ticket-4  `:safety-test-not-passed`            -- the screening itself finds a
                                                     failed post-repair safety test
    ticket-5  `:flammability-compliance-unconfirmed` -- a reupholstered ticket whose
                                                     flammability compliance is
                                                     unconfirmed
    ticket-1  `:already-completed`                 -- second completion of the same ticket
    ticket-1  `:already-returned`                  -- second return of the same ticket"
  []
  (let [db (store/seed-db)
        actor (op/build db)
        runs (atom [])
        step! (fn [f] (swap! runs conj (f)) nil)]
    (step! #(exec! actor "t1-intake" {:op :ticket/intake :subject "ticket-1"
                                      :patch {:id "ticket-1" :customer "Sakura Tanaka"}}))

    (step! #(exec! actor "t1-assess" {:op :jurisdiction/assess :subject "ticket-1"}))
    (step! #(approve! actor "t1-assess"))

    (step! #(exec! actor "t1-safety" {:op :safety/screen :subject "ticket-1"}))
    (step! #(approve! actor "t1-safety"))

    (step! #(exec! actor "t1-flammability" {:op :flammability/screen :subject "ticket-1"}))
    (step! #(approve! actor "t1-flammability"))

    (step! #(exec! actor "t1-complete" {:op :repair/complete :subject "ticket-1"}))
    (step! #(approve! actor "t1-complete"))

    (step! #(exec! actor "t1-return" {:op :item/return :subject "ticket-1"}))
    (step! #(approve! actor "t1-return"))

    (step! #(exec! actor "t2-assess" {:op :jurisdiction/assess :subject "ticket-2" :no-spec? true}))

    (step! #(exec! actor "t3-assess" {:op :jurisdiction/assess :subject "ticket-3"}))
    (step! #(approve! actor "t3-assess"))
    (step! #(exec! actor "t3-complete" {:op :repair/complete :subject "ticket-3"}))

    (step! #(exec! actor "t4-safety" {:op :safety/screen :subject "ticket-4"}))

    (step! #(exec! actor "t5-flammability" {:op :flammability/screen :subject "ticket-5"}))

    (step! #(exec! actor "t1-complete-again" {:op :repair/complete :subject "ticket-1"}))
    (step! #(exec! actor "t1-return-again" {:op :item/return :subject "ticket-1"}))

    {:db db :runs @runs}))

;; ----------------------------- derivation -----------------------------

(defn- hold-facts [ledger]
  (filterv #(= :governor-hold (:t %)) ledger))

(defn- commit-facts [ledger]
  (filterv #(= :committed (:t %)) ledger))

(defn- distinct-hold-rules [ledger]
  (vec (sort (distinct (mapcat :basis (hold-facts ledger))))))

(defn- last-fact-for [ledger ticket-id]
  (last (filter #(= (:subject %) ticket-id) ledger)))

(def ^:private approver-key-names
  "Key spellings that would count as the human approver's id having
  reached a committed record."
  #{"approved-by" "approved_by" "approver" "approved-by-id" "approved_by_id"})

(defn- key-names
  "Key names of `m` as strings, tolerating both keyword-keyed maps (the
  screening/assessment payloads) and string-keyed maps (the
  `furniture.registry` draft records)."
  [m]
  (when (map? m)
    (mapv #(if (keyword? %) (name %) (str %)) (keys m))))

(defn- has-approver? [m]
  (boolean (some approver-key-names (key-names m))))

(defn- registers
  "Every committed SSoT register this run wrote, read back out THROUGH
  the `furniture.store/Store` protocol -- not out of the MemStore atom
  -- so the disclosure below measures what any backend actually
  retained."
  [db]
  (let [ids (mapv :id (store/all-tickets db))]
    (vec
     (concat
      (for [id ids :let [v (store/assessment-of db id)] :when v]
        {:effect ":assessment/set" :subject id :value v})
      (for [id ids :let [v (store/safety-screening-of db id)] :when v]
        {:effect ":safety-screening/set" :subject id :value v})
      (for [id ids :let [v (store/flammability-screening-of db id)] :when v]
        {:effect ":flammability-screening/set" :subject id :value v})
      (for [r (store/completion-history db)]
        {:effect ":ticket/mark-completed" :subject (get r "ticket_id") :value r})
      (for [r (store/return-history db)]
        {:effect ":ticket/mark-returned" :subject (get r "ticket_id") :value r})))))

(defn- approver-attribution
  "DERIVED honest disclosure about where the human approver's id
  actually ended up.

  `furniture.operation`'s `:request-approval` node attaches the
  approver at `[:payload :approved-by]` on the record. Whether that
  survives is per-effect and is NOT assumed here -- each register is
  read back through the protocol and scanned for an approver key, so
  this page self-corrects if the store is later changed. The approver
  ids themselves are joined from the run's own `:approval-granted`
  audit facts.

  Returns `{:approvers .. :registers .. :retained .. :dropped ..
  :on-ledger? ..}`."
  [db runs]
  (let [audit (mapcat #(get-in % [:state :audit]) runs)
        approvers (vec (sort (distinct (keep #(when (= :approval-granted (:t %)) (:by %)) audit))))
        regs (registers db)
        ledger (store/ledger db)]
    {:approvers  approvers
     :registers  regs
     :retained   (filterv #(has-approver? (:value %)) regs)
     :dropped    (filterv #(not (has-approver? (:value %))) regs)
     :on-ledger? (boolean (some #(= :approval-granted (:t %)) ledger))}))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- code [v] (str "<code>" (esc v) "</code>"))
(defn- ok [v] (str "<span class=\"ok\">" v "</span>"))
(defn- warn [v] (str "<span class=\"warn\">" v "</span>"))
(defn- crit [v] (str "<span class=\"critical\">" v "</span>"))
(defn- muted [v] (str "<span class=\"muted\">" v "</span>"))
(defn- num [v] (str "<span class=\"num\">" (esc v) "</span>"))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       body
       "  </section>\n"))

;; --- run summary -------------------------------------------------------

(defn- summary-rows [db ledger]
  [(row "Ledger facts (append-only)" (num (count ledger)))
   (row "Committed ops" (num (count (commit-facts ledger))))
   (row "Governor HARD holds" (crit (num (count (hold-facts ledger)))))
   (row "Distinct HARD rules fired"
        (str (num (count (distinct-hold-rules ledger))) " &middot; "
             (str/join " " (map #(code (str %)) (distinct-hold-rules ledger)))))
   (row "Draft repair-completion records" (num (count (store/completion-history db))))
   (row "Draft item-return records" (num (count (store/return-history db))))
   (row "Tickets in the seeded directory" (num (count (store/all-tickets db))))])

;; --- tickets -----------------------------------------------------------

(defn- lifecycle-cell [{:keys [repair-completed? item-returned?]}]
  (cond
    item-returned?    (ok "repaired &amp; returned")
    repair-completed? (warn "repaired, not yet returned")
    :else             (muted "in repair")))

(defn- status-cell [ledger ticket-id]
  (let [f (last-fact-for ledger ticket-id)]
    (cond
      (nil? f) (muted "no activity")
      (= :governor-hold (:t f))
      (crit (str "HARD hold &middot; " (esc (name (or (-> f :violations first :rule) :unknown)))))
      (= :committed (:t f)) (ok "committed")
      :else (muted "in progress"))))

(defn- ticket-row [ledger {:keys [id customer item item-type jurisdiction parts-quantity
                                  parts-unit-price claimed-parts-cost safety-test-passed?
                                  involves-upholstery-work? flammability-compliance-confirmed?]
                           :as t}]
  (let [recomputed (registry/compute-parts-cost t)
        match? (registry/parts-cost-matches-claim? t)]
    (row (code id)
         (esc customer)
         (esc item)
         (code (str item-type))
         (code jurisdiction)
         (str (num parts-quantity) " &times; " (num parts-unit-price) " = " (num recomputed))
         (if match?
           (ok (str "claimed " (esc claimed-parts-cost)))
           (crit (str "claimed " (esc claimed-parts-cost) " &ne; " (esc recomputed))))
         (if safety-test-passed? (ok "passed") (crit "FAILED"))
         (if involves-upholstery-work?
           (if flammability-compliance-confirmed?
             (ok "upholstery &middot; confirmed")
             (crit "upholstery &middot; UNCONFIRMED"))
           (muted "no upholstery work &mdash; n/a"))
         (lifecycle-cell t)
         (status-cell ledger id))))

;; --- holds -------------------------------------------------------------

(defn- hold-row [{:keys [op subject violations confidence]}]
  (let [{:keys [rule detail]} (first violations)]
    (row (code (str op))
         (code subject)
         (crit (code (str rule)))
         (esc detail)
         (num confidence))))

;; --- action gate -------------------------------------------------------

(defn- gate-row
  "The gate description is derived from `furniture.phase/phases` at
  render time, so it cannot drift from the code that enforces it."
  [op note]
  (let [ph (get phase/phases phase/default-phase)
        writable? (contains? (:writes ph) op)
        auto? (contains? (:auto ph) op)]
    (row (code (str op))
         (cond
           (not writable?) (crit "not writable at this phase")
           auto?           (ok "auto-commit when governor-clean")
           :else           (warn "human approval required"))
         (esc note))))

(def ^:private gate-notes
  [[:ticket/intake "no capital risk -- the only member of any phase's :auto set"]
   [:jurisdiction/assess "must cite an official spec-basis in furniture.facts"]
   [:safety/screen "the screening op itself HARD-holds on its own failed finding"]
   [:flammability/screen "conditional: only tickets that declare :involves-upholstery-work? true"]
   [:repair/complete "ALWAYS human -- never auto at any phase; parts cost independently recomputed"]
   [:item/return "ALWAYS human -- never auto at any phase; safety test and flammability compliance re-checked"]])

;; --- jurisdictions -----------------------------------------------------

(defn- jurisdiction-row [iso3]
  (let [{:keys [name owner-authority legal-basis required-evidence provenance]} (facts/spec-basis iso3)
        flam (facts/flammability-spec-basis iso3)]
    (row (code iso3)
         (esc name)
         (esc owner-authority)
         (esc legal-basis)
         (num (count required-evidence))
         (if flam
           (ok (esc (:flammability-legal-basis flam)))
           (muted "no flammability regime in this R0 catalog &mdash; not fabricated"))
         (str "<a href=\"" (esc provenance) "\">source</a>"))))

;; --- approver attribution ---------------------------------------------

(defn- attribution-row [{:keys [effect subject value]}]
  (row (code effect)
       (code subject)
       (if (has-approver? value)
         (ok "approver key present on the committed record")
         (crit "approver key absent &mdash; audit only, not retained in record"))
       (esc (str/join ", " (sort (key-names value))))))

(defn- attribution-note [{:keys [approvers retained dropped on-ledger?]}]
  (str
   (cond
     (empty? approvers)
     "This run produced no human approval, so there is no approver to attribute."

     (and (seq retained) (empty? dropped))
     (str "The approver id " (str/join " " (map code approvers))
          " is retained on every committed register.")

     (seq retained)
     (str "The approver id " (str/join " " (map code approvers))
          " is retained on " (count retained) " of " (+ (count retained) (count dropped))
          " committed registers. On the remaining " (count dropped)
          " it is <strong>not</strong> retained: <code>furniture.operation</code>'s "
          "<code>:request-approval</code> node attaches it at "
          "<code>[:payload :approved-by]</code>, but those effects do not persist "
          "<code>:payload</code> &mdash; <code>:ticket/mark-completed</code> and "
          "<code>:ticket/mark-returned</code> rebuild their record from "
          "<code>furniture.registry</code>, whose draft record has a fixed key set with "
          "no approver field. The id shown above is joined from this run&rsquo;s "
          "<code>:approval-granted</code> audit fact, and is labelled "
          "<em>audit only &mdash; not retained in record</em> rather than silently omitted: "
          "a reader must be able to tell &ldquo;nobody approved&rdquo; from "
          "&ldquo;the store did not keep it&rdquo;.")

     :else
     (str "The approver id " (str/join " " (map code approvers))
          " reached <strong>no</strong> committed register."))
   " "
   (if on-ledger?
     "It is also present on the persisted audit ledger as an <code>:approval-granted</code> fact."
     (str "It is <strong>not</strong> on the persisted audit ledger either: "
          "<code>furniture.operation</code>&rsquo;s <code>:commit</code> node appends only the "
          "<code>:committed</code> fact, so <code>:approval-granted</code> lives in the run&rsquo;s "
          "<code>:audit</code> channel and never reaches <code>store/append-ledger!</code>."))
   " This row set is walked at render time, so the page self-corrects if the store is fixed."))

;; --- records / ledger --------------------------------------------------

(defn- record-row [r]
  (row (code (get r "record_id"))
       (code (get r "kind"))
       (code (get r "ticket_id"))
       (code (get r "jurisdiction"))
       (if (get r "immutable") (ok "immutable") (muted "mutable"))))

(defn- ledger-row [{:keys [t op subject disposition basis summary]}]
  (row (case t
         :committed (ok (esc (clojure.core/name t)))
         :governor-hold (crit (esc (clojure.core/name t)))
         (muted (esc (clojure.core/name t))))
       (code (str op))
       (code subject)
       (esc (or (some->> basis (map #(if (keyword? %) (clojure.core/name %) (str %))) (str/join ", "))
                (some-> disposition clojure.core/name)
                ""))
       (esc (or summary ""))))

;; --- document ----------------------------------------------------------

(defn render
  "Renders the whole operator-console.html document from the
  `{:db .. :runs ..}` map `run-demo!` returned."
  [{:keys [db runs]}]
  (let [ledger (vec (store/ledger db))
        tickets (store/all-tickets db)
        holds (hold-facts ledger)
        attribution (approver-attribution db runs)
        cov (facts/coverage)]
    (str
     "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-9524 &middot; furniture &amp; home-furnishings repair</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Repair of furniture and home furnishings (ISIC 9524) &mdash; Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample &middot; governor-gated &middot; repair completion / item return always human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "This run"
      (str "Build-time generated from the real actor stack &mdash; "
           (code "furniture.operation") " &rarr; " (code "furniture.governor") " &rarr; "
           (code "furniture.store") ", driven through langgraph "
           (code "g/run*") " by " (code "furniture.render-html")
           " (" (code "clojure -M:dev:render-html") "). No hand-written rows.")
      (table ["Measure" "Value"] (summary-rows db ledger)))

     (section
      "Repair tickets"
      (str "The seeded ticket directory (" (code "furniture.store/demo-data") ") after this run. "
           "Parts arithmetic is re-derived here by " (code "furniture.registry/compute-parts-cost")
           ", the same independent recompute the governor uses &mdash; the claimed figure is never trusted. "
           "The item description and the upholstery flag are <strong>declared seed fields</strong>, not "
           "lookups: this repo has no material, wood-species or textile registry, and none is implied.")
      (table ["Ticket" "Customer" "Item" "Type" "Juris." "Parts qty &times; unit = recomputed"
              "Claimed parts cost" "Post-repair safety test" "Flammability compliance"
              "Lifecycle" "Last op"]
             (mapv (partial ticket-row ledger) tickets)))

     (section
      (str "Governor HARD holds this run (" (count holds) ")")
      (str "Every row is a " (code ":governor-hold") " fact on the append-only ledger. "
           "HARD violations cannot be overridden &mdash; these proposals never reached a human at all. "
           "The rule, the detail text and the confidence are the governor&rsquo;s own output.")
      (table ["Op" "Ticket" "Rule" "Detail (governor)" "LLM confidence"]
             (mapv hold-row holds)))

     (section
      "Action gate"
      (str "Derived from " (code "furniture.phase/phases") " at phase "
           (code phase/default-phase) " (" (esc (:label (get phase/phases phase/default-phase)))
           "). " (code ":repair/complete") " and " (code ":item/return")
           " are deliberately absent from every phase&rsquo;s " (code ":auto")
           " set &mdash; a permanent structural fact, not a rollout milestone. The governor&rsquo;s "
           (code "high-stakes") " gate enforces the same invariant independently.")
      (table ["Op" "Phase-3 gate" "Why"]
             (mapv (fn [[op note]] (gate-row op note)) gate-notes)))

     (section
      "Jurisdiction spec-basis catalog"
      (str (esc (:note cov)) " A jurisdiction absent from this table has NO spec-basis, and the governor "
           "HARD-holds any proposal that tries to invent one &mdash; which is exactly what "
           (code "ticket-2") " (jurisdiction " (code "ATL") ") demonstrates above.")
      (table ["ISO3" "Jurisdiction" "Owner authority" "Legal basis" "Required evidence"
              "Furniture-flammability regime" "Provenance"]
             (mapv jurisdiction-row (sort (keys facts/catalog)))))

     (section
      "Committed registers &amp; approver attribution"
      (str "Each committed register is read back <em>through the "
           (code "furniture.store/Store") " protocol</em> and scanned for an approver key. "
           (attribution-note attribution))
      (table ["Effect" "Ticket" "Approver attribution" "Keys actually retained"]
             (mapv attribution-row (:registers attribution))))

     (section
      "Draft repair-completion records"
      (str "Produced by " (code "furniture.registry/register-repair-completion")
           ". Every certificate this actor emits is UNSIGNED &mdash; signature is the shop&rsquo;s act, "
           "not this actor&rsquo;s.")
      (table ["Record id" "Kind" "Ticket" "Jurisdiction" "Immutability"]
             (mapv record-row (store/completion-history db))))

     (section
      "Draft item-return records"
      (str "Produced by " (code "furniture.registry/register-item-return") ", same posture.")
      (table ["Record id" "Kind" "Ticket" "Jurisdiction" "Immutability"]
             (mapv record-row (store/return-history db))))

     (section
      (str "Audit ledger (" (count ledger) " facts)")
      "Append-only decision-fact log &mdash; every commit and every hold this scenario produced, in order."
      (table ["Fact" "Op" "Ticket" "Basis" "Summary"] (mapv ledger-row ledger)))

     "</main>\n"
     "<footer>\n"
     "  <p>Generated by <code>furniture.render-html</code> from a fresh <code>furniture.store/seed-db</code>. "
     "Deterministic: no timestamps, no random ids, byte-identical across reruns against the same seed. "
     "The build refuses to write this page if the run produced zero <code>:governor-hold</code> facts.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        holds (hold-facts ledger)]
    ;; BUILD-TIME INVARIANT, not a convention: a console that shows no
    ;; real HARD hold is a page about a governor that never governed.
    ;; Refuse to write rather than emit a plausible-looking page.
    (when (zero? (count holds))
      (throw (ex-info (str "no :governor-hold fact on the ledger -- refusing to write a console "
                           "that shows no real hold")
                      {:out out
                       :ledger-facts (count ledger)
                       :committed (count (commit-facts ledger))})))
    (let [html (render result)]
      (spit out html)
      (println "wrote" out
               (str "(" (count ledger) " ledger facts, "
                    (count holds) " HARD holds over "
                    (count (distinct-hold-rules ledger)) " distinct rules "
                    (pr-str (distinct-hold-rules ledger)) ", "
                    (count (store/completion-history db)) " repair completions, "
                    (count (store/return-history db)) " item returns)")))))
