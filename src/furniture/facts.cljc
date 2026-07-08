(ns furniture.facts
  "Per-jurisdiction consumer-product-safety AND furniture-flammability-
  compliance regulatory catalog -- the G2-style spec-basis table the
  Repair Shop Governor checks every `:jurisdiction/assess` proposal
  against ('did the advisor cite an OFFICIAL public source for this
  jurisdiction's requirements, or did it invent one?'), closely
  modeled on `cloud-itonami-isic-9522`'s `applianceshop.facts`.

  This blueprint's own named activity (furniture and home furnishings
  repair) routinely includes reupholstery/filling-material-replacement
  work, a real, distinct regulatory concern beyond `repairshop`/9521's,
  `commrepair`/9512's and `applianceshop`/9522's own catalogs: several
  major jurisdictions require reupholstered furniture to carry a
  compliance label/tag confirming the replacement filling/upholstery
  materials meet a flammability standard (California's Home Furnishings
  and Thermal Insulation Act 'Law Label' regime is the textbook example
  -- one of the oldest, most well-documented furniture-flammability-
  compliance regimes in consumer-product-repair law). Each jurisdiction
  entry below therefore cites BOTH the consumer-product-safety law this
  fleet's repair-shop catalogs already model AND a SEPARATE furniture-
  flammability-compliance law where one actually exists.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries. Japan is
  DELIBERATELY seeded WITHOUT a `:flammability-*` sub-citation:
  unlike the US/UK/Germany, Japan has no direct equivalent mandatory
  furniture-flammability-compliance-labeling regime for residential
  reupholstery work in this R0 catalog -- inventing one to make
  coverage look bigger would be the exact fabrication this discipline
  forbids. See `furniture.governor/flammability-compliance-
  unconfirmed-violations` for how this is handled as a CONDITIONAL
  check rather than a universal one.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  diagnostic-report/parts-used-documentation/post-repair-safety-test-
  record evidence set (PLUS a flammability-compliance-record item
  where a jurisdiction actually has such a regime); `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:jurisdiction/assess` proposal can commit.
  `:flammability-owner-authority` / `:flammability-legal-basis` /
  `:flammability-provenance` are the SEPARATE furniture-flammability-
  compliance citation the governor's `flammability-compliance-
  unconfirmed?` check is grounded in -- present ONLY for jurisdictions
  that actually have such a regime."
  {"JPN" {:name "Japan"
          :owner-authority "経済産業省 (Ministry of Economy, Trade and Industry, METI)"
          :legal-basis "消費生活用製品安全法 (Consumer Product Safety Act)"
          :national-spec "家具製品の一般消費生活用製品安全基準"
          :provenance "https://www.meti.go.jp/product_safety/"
          :required-evidence ["故障診断書 (diagnostic report)"
                              "使用部品記録 (parts-used documentation)"
                              "修理後安全試験記録 (post-repair safety-test record)"]}
   "USA" {:name "United States"
          :owner-authority "U.S. Consumer Product Safety Commission (CPSC)"
          :legal-basis "Consumer Product Safety Act (15 U.S.C. §§2051 et seq.)"
          :national-spec "CPSC product-safety standards for household furniture"
          :provenance "https://www.cpsc.gov/Regulations-Laws--Standards/Statutes"
          :required-evidence ["Diagnostic report"
                              "Parts-used documentation"
                              "Post-repair safety-test record"
                              "Flammability-compliance record"]
          :flammability-owner-authority "California Bureau of Household Goods and Services (BHGS)"
          :flammability-legal-basis "California Business and Professions Code -- Home Furnishings and Thermal Insulation Act (Technical Bulletin 117-2013 flammability standard, registered-reupholsterer 'Law Label' requirement)"
          :flammability-provenance "https://www.bhgs.dca.ca.gov/about_us/lawlabel.shtml"}
   "GBR" {:name "United Kingdom"
          :owner-authority "Office for Product Safety and Standards (OPSS)"
          :legal-basis "General Product Safety Regulations 2005"
          :national-spec "OPSS product-safety enforcement standards for furniture"
          :provenance "https://www.gov.uk/government/organisations/office-for-product-safety-and-standards"
          :required-evidence ["Diagnostic report"
                              "Parts-used documentation"
                              "Post-repair safety-test record"
                              "Flammability-compliance record"]
          :flammability-owner-authority "Office for Product Safety and Standards (OPSS) / local Trading Standards"
          :flammability-legal-basis "The Furniture and Furnishings (Fire) (Safety) Regulations 1988 (as amended)"
          :flammability-provenance "https://www.gov.uk/guidance/furniture-and-furnishings-fire-safety-regulations-guidance"}
   "DEU" {:name "Germany"
          :owner-authority "Marktüberwachungsbehörden der Länder"
          :legal-basis "Produktsicherheitsgesetz (ProdSG)"
          :national-spec "ProdSG Marktüberwachungsanforderungen für Möbel"
          :provenance "https://www.baua.de/DE/Themen/Anwendungssichere-Chemikalien-und-Produkte/Produktsicherheit/Produktsicherheit_node.html"
          :required-evidence ["Diagnosebericht (diagnostic report)"
                              "Ersatzteilnachweis (parts-used documentation)"
                              "Sicherheitsprüfungsprotokoll nach Reparatur (post-repair safety-test record)"
                              "Brandschutz-Konformitätsnachweis (flammability-compliance record)"]
          :flammability-owner-authority "Marktüberwachungsbehörden der Länder"
          :flammability-legal-basis "DIN EN 1021-1/1021-2 (Zündquellen-Prüfverfahren für Polstermöbel) unter ProdSG"
          :flammability-provenance "https://www.baua.de/DE/Themen/Anwendungssichere-Chemikalien-und-Produkte/Produktsicherheit/Produktsicherheit_node.html"}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to complete a
  repair or return an item on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-9524 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `furniture.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

(defn flammability-spec-basis
  "The jurisdiction's flammability-compliance requirement map, or nil
  -- nil means this jurisdiction has NO formal furniture-flammability-
  compliance-labeling regime this catalog is aware of (honestly true
  for Japan as of this R0 catalog, unlike the US/UK/Germany)."
  [iso3]
  (when-let [sb (spec-basis iso3)]
    (when (:flammability-owner-authority sb)
      (select-keys sb [:flammability-owner-authority :flammability-legal-basis :flammability-provenance]))))
