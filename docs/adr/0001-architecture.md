# ADR-0001: RepairOps-LLM ⊣ Repair Shop Governor architecture

## Status

Accepted. `cloud-itonami-isic-9524` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-9524` publishes an OSS business blueprint for
repair of furniture and home furnishings. Like every prior actor in
this fleet, the blueprint alone is not an implementation: this ADR
records the governed-actor architecture that promotes it to real,
tested code, following the same langgraph StateGraph + independent
Governor + Phase 0→3 rollout pattern established by `cloud-itonami-
isic-6511` (life insurance) and applied across eighty-one prior
siblings, most recently `cloud-itonami-isic-8549` (other education
n.e.c.).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:repair-shop-governor`, is IDENTICAL to `repairshop`/9521's (consumer
electronics), `commrepair`/9512's (communication equipment) and
`applianceshop`/9522's (household appliances). Per the fleet-wide
governor-name-reuse precedent `commrepair`/9512's own ADR-0001
established -- confirmed five times since across two other governor-
name families -- sharing a governor name is acceptable when the
underlying business archetype is genuinely the same, provided the
reuse is documented and the new build brings its own genuinely
differentiated, well-grounded check. This build is the SEVENTH
confirmation overall, and the FIRST time the precedent applies within
the ORIGINAL `:repair-shop-governor` family for a THIRD sibling.

## Decision

### Decision 1: governor-name reuse -- seventh confirmation, third sibling in the original family

`repairshop`/9521, `commrepair`/9512, `applianceshop`/9522 and
`furniture`/9524 all share the IDENTICAL `:repair-shop-governor`
keyword -- a deliberate, honest reuse of the same repair-shop business
archetype (diagnose, quote/assess, repair, return) applied to a
different repair-item category each time (consumer electronics,
communication equipment, household appliances, furniture and home
furnishings). This is the same reasoning `commrepair`/9512's and
`applianceshop`/9522's own ADR-0001s established, now confirmed a
third time within this specific family.

### Decision 2: dual-actuation shape

This blueprint's own README, business-model.md and operator-guide.md
consistently name two real-world acts: "performing a repair or
returning an item to the customer." Matching `repairshop`/9521's,
`commrepair`/9512's and `applianceshop`/9522's own dual-actuation
shape, `high-stakes` here is a two-member set,
`#{:actuation/complete-repair :actuation/return-item}`.

### Decision 3: `parts-cost-matches-claim?` and `safety-test-not-passed?` -- honest, literal reuses

`furniture.registry/parts-cost-matches-claim?` and `furniture.
governor/safety-test-not-passed-violations` are HONEST, LITERAL reuses
of `repairshop.registry`'s/`commrepair.registry`'s/`applianceshop.
registry`'s own EXACT-MATCH independent-recompute checks -- NOT
claimed as new. A furniture-repair ticket's own claimed parts cost
(hardware, fabric yardage, foam/filling material) against
quantity-times-unit-price, and a post-repair structural-stability
safety test (e.g. a repaired chair leg or recliner mechanism must not
collapse under normal use), are the SAME real-world concerns as
`applianceshop`/9522's own checks, reapplied to a different repair-
item category.

### Decision 4: entity and op shape

The primary entity is a `ticket`. Six ops: `:ticket/intake` (directory
upsert, no capital risk), `:jurisdiction/assess` (per-jurisdiction
consumer-product-safety/flammability-compliance evidence checklist,
never auto), `:safety/screen` (post-repair safety screening, honest
reuse, never auto), `:flammability/screen` (flammability-compliance
screening, GENUINELY NEW, never auto), `:repair/complete` (POSITIVE,
high-stakes), and `:item/return` (POSITIVE, high-stakes).

### Decision 5: `flammability-compliance-unconfirmed?` -- the 67th unconditional-evaluation grounding, a genuinely new concept, the FOURTH conditional variant

Before writing this check, every prior sibling's governor namespace
across the entire fleet was grepped for any check function named
`flammability`, `fire-safety`, `reupholster` or `upholstery` -- zero
hits, confirming this is a genuinely new concept.
`flammability-compliance-unconfirmed-violations` reuses the
unconditional-evaluation-screening DISCIPLINE (`casualty.governor/
sanctions-violations`'s original fix) for the 67th distinct
application overall (most recently `training.governor/instructor-
license-unconfirmed-violations` at 66th). This is the FOURTH
conditional variant (after `socialresearch`/7220's, `bizassoc`/9411's
and `training`/8549's own, at 63rd, 64th and 66th) -- CONDITIONAL on
the ticket's own `:involves-upholstery-work? true` ground truth: a
repair that does not touch filling/upholstery materials (e.g. a simple
wood-joint reglue) has no flammability-compliance requirement at all.
Grounded in real furniture-flammability-compliance law: California's
Home Furnishings and Thermal Insulation Act (Law Label regime,
Technical Bulletin 117-2013), UK's Furniture and Furnishings (Fire)
(Safety) Regulations 1988, Germany's DIN EN 1021-1/1021-2 ignition-
source test standards under ProdSG -- honestly ABSENT for Japan, which
has no direct equivalent mandatory regime in this R0 catalog. Gates
`:flammability/screen` and `:repair/complete`/`:item/return`.

### Decision 6: dedicated double-actuation-guard booleans

`:repair-completed?`/`:item-returned?` are dedicated booleans on the
`ticket` record, never a single `:status` value -- an honest, literal
reuse of `applianceshop.governor`'s own guards, informed by `cloud-
itonami-isic-6492`'s real status-lifecycle bug (ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`furniture.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.
db`-backed), proven to satisfy the same contract in
`test/furniture/store_contract_test.clj` -- the same seam every
sibling actor uses so swapping the SSoT backend is a configuration
change, not a rewrite.

### Decision 8: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:ticket/intake` (no
capital risk). `:jurisdiction/assess`, `:safety/screen` and
`:flammability/screen` are never auto-eligible at any phase (matching
every sibling's screening-op posture), and `:repair/complete`/`:item/
return` are permanently excluded from every phase's `:auto` set -- a
structural fact, not a rollout milestone, enforced by BOTH `furniture.
phase` and `furniture.governor`'s `high-stakes` set independently.

### Decision 9: no bespoke domain capability lib, and no `blueprint.edn` field-sync fixes needed

This blueprint's own `:itonami.blueprint/required-technologies` names
no domain-specific capability beyond the generic robotics/identity/
forms/dmn/bpmn/audit-ledger stack -- there was no capability-lib
decision to make at all. This repo's `blueprint.edn` already had the
correct `isic-` prefixed `:id` and correctly populated `:required-
technologies`/`:optional-technologies` matching the `kotoba-lang/
industry` registry's own entry for `"9524"` exactly -- only the
`:maturity` field itself needed adding.

### Decision 10: mock + LLM advisor pair

`furniture.repairopsllm` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-
completing a repair or auto-returning an item).

## Alternatives considered

- **An unconditional flammability-compliance check** (applying to
  every ticket regardless of whether the repair actually touches
  upholstery). Rejected: a simple wood-joint reglue or hardware
  replacement has no flammability-compliance concern at all -- forcing
  the check onto every ticket would fabricate a requirement.
- **Declining the build and leaving the repair-shop cluster
  exhausted at two siblings.** Rejected: `commrepair`/9512's and
  `applianceshop`/9522's own ADR-0001s already established that the
  governor-name-collision constraint was a self-imposed convention,
  not a structural requirement -- extending it to a third sibling
  confirms the precedent generalizes rather than being limited to two
  instances.
- **A single "consumer-product-safety" check merging the safety-test
  and flammability-compliance concerns.** Rejected: the safety test is
  an unconditional, universally-applicable check; flammability
  compliance is conditional on the ticket's own upholstery-work ground
  truth -- merging them would lose the conditional scoping.

## Consequences

- Eighty-third actor in this fleet (82 implemented before this
  build).
- Confirms the fleet-wide governor-name-reuse precedent a seventh
  time, and confirms it generalizes to a THIRD sibling within the
  original `:repair-shop-governor` family.
- Establishes a genuinely NEW conditional unconditional-evaluation-
  screening concept (flammability-compliance-unconfirmed?), grep-
  verified absent from every prior sibling before the claim was
  finalized.
- `MemStore` ‖ `DatomicStore` parity is proven by
  `test/furniture/store_contract_test.clj`, the same `:db-api`-driven
  swap pattern every sibling actor uses.
- 41 tests / 191 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean dual-actuation lifecycle plus
  five HARD-hold scenarios end-to-end.
- `blueprint.edn` required no field-sync fixes this time (already
  correct) -- only the `:maturity` flip itself.
- Two repair-shop candidates remain open for future builds: 9523
  (repair of footwear and leather goods) and 9529 (repair of other
  personal and household goods), each still requiring its own
  genuinely differentiated check.
