# Personal Ranking Model — v1.16.0 → v1.17.0 embeddings foundation

## Pipeline

```text
Last.fm similar candidates
  -> top-5 tag enrichment (bounded, cached)
  -> novelty / recent-track / artist cooldown
  -> PersonalTasteProfile from recommendation-feedback.tsv
  -> hybrid score + 64D vector cosine
  -> RecommendationCandidate
  -> ytsearch:
  -> existing playback pipeline
```

## Personal evidence

The model aggregates only recommendation feedback already captured by v1.15:

- exact track affinity;
- artist affinity;
- tag affinity when Last.fm metadata is available;
- positive/negative signal counts used as model confidence.

No Discord message content, voice telemetry or external user profile is used.

## Exploration vs exploitation

`discovery` starts with the highest exploration rate, `similar` with a smaller one. As bounded feedback evidence grows, confidence rises and exploration falls. A high negative-signal ratio can raise exploration again so the model can escape a bad local preference cluster.

Hard novelty is still authoritative: a known track rejected by `discovery` cannot be resurrected by a positive personal score.

## Explainability

`/radio why` exposes the components that affected the last candidate. `/radio model` shows model confidence and strongest learned artist/tag affinities without exposing persistent raw records.

## Persistence

`recommendation-feedback.tsv` is V2 and stores bounded tags. V1 journals are read transparently and become V2 on the next write. Downgrading to v1.15 after a V2 write requires restoring the pre-v1.16 backup.


## v1.17 embeddings foundation

`feature-hash-v1` is a deterministic local 64-dimensional embedding over normalized artist/title/tags. It is deliberately not presented as a neural semantic model. Its purpose is to establish a provider-neutral vector API, taste-vector derivation and bounded cosine contribution before a future semantic/remote/local ML provider is introduced.

The personal taste vector is rebuilt from the existing bounded `PersonalTasteProfile`; no new persistence format is introduced in v1.17. Positive and negative feedback therefore move the vector in opposite directions while the existing exact track/artist/tag affinity remains independently explainable.

Vector similarity is an additional bounded score only. Recent-track rejection and `discovery` hard novelty happen before vector scoring, so embeddings cannot resurrect a track that policy already rejected. `/radio why` exposes the vector similarity/confidence when it materially contributed, and `/radio model` exposes provider, dimensions, contributing features and vector confidence.
