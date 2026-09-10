(ns furniture.facts-test
  (:require [clojure.test :refer [deftest is]]
            [furniture.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest jpn-has-no-flammability-spec-basis
  (is (nil? (facts/flammability-spec-basis "JPN"))
      "Japan has no formal furniture-flammability-compliance-labeling regime in this R0 catalog -- must not be fabricated"))

(deftest usa-gbr-deu-each-have-a-flammability-spec-basis
  (doseq [iso3 ["USA" "GBR" "DEU"]]
    (is (some? (facts/flammability-spec-basis iso3)) (str iso3 " flammability-spec-basis"))
    (is (string? (:flammability-provenance (facts/flammability-spec-basis iso3))) (str iso3 " flammability-provenance"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest unknown-jurisdiction-has-no-flammability-spec-basis
  (is (nil? (facts/flammability-spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))
