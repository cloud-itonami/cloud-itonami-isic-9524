# Business Model: Repair of furniture and home furnishings

## Classification

- Repository: `cloud-itonami-isic-9524`
- ISIC Rev.5: `9524`
- Activity: repair of furniture and home furnishings -- diagnosing and repairing furniture and home furnishings for customers
- Social impact: community access, data sovereignty, transparent audit

## Customer

- independent furniture-repair/restoration shops
- cooperative repair collectives
- community right-to-repair programs

## Offer

- item intake
- diagnostic/quote proposal
- repair-completion proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per shop
- support: monthly retainer with SLA
- migration: import from an incumbent repair-shop system
- per-repair fee

## Trust Controls

- no repair is performed and no item is returned without human sign-off
- a fabricated diagnostic forces a hold, not an override
- a reupholstered/refilled item cannot be returned without a confirmed
  flammability-compliance status on file -- unconfirmed, this is a
  hold, never an override
- every repair path is auditable
- emergency manual override paths remain outside LLM control

## Repair Shop Governor: decision rule

This vertical's governor shares its name (`:repair-shop-governor`)
with `cloud-itonami-isic-9521`'s (consumer electronics),
`cloud-itonami-isic-9512`'s (communication equipment) and
`cloud-itonami-isic-9522`'s (household appliances) -- a deliberate,
honest reuse of the same repair-shop business archetype applied to a
different repair-item category each time, not a naming error. The
genuinely distinguishing concern this vertical adds is flammability
compliance: furniture and home-furnishings repair routinely includes
reupholstery/filling-material replacement work, and several major
jurisdictions require reupholstered items to carry a confirmed
flammability-compliance status (a compliance label/tag) before
returning the item to the customer. This requirement is CONDITIONAL:
a repair that does not touch filling/upholstery materials (e.g. a
simple wood-joint reglue) carries no such requirement at all.
