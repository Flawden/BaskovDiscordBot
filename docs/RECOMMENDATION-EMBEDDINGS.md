# Recommendation Embeddings Foundation — v1.17.0

## Goal

v1.17 introduces a vector contract without pretending that a lightweight Discord bot already owns a trained neural recommender. The default provider is `feature-hash-v1`, a deterministic local 64D embedding over normalized artist/title/tags.

The important architectural result is the boundary:

```text
RecommendationCandidate metadata
  -> RecommendationEmbeddingProvider
  -> 64D candidate vector

bounded recommendation feedback
  -> PersonalTasteProfile
  -> PersonalTasteVectorModel
  -> 64D taste vector

candidate vector + taste vector
  -> bounded cosine similarity
  -> RecommendationRanker
  -> RecommendationCandidate
  -> ytsearch:
  -> existing load / policy / queue / voice pipeline
```

## Why local feature hashing first

- zero model downloads and zero new network dependency;
- deterministic output across restarts;
- small fixed memory footprint;
- exact same ranking API can later accept a semantic embedding provider;
- failures in a future provider can fall back to the local implementation without touching playback.

`feature-hash-v1` is therefore an embeddings **foundation**, not a claim of semantic understanding.

## Personal taste vector

The taste vector is rebuilt from existing v1.16 feedback-derived affinity maps. It does not create a fifth persistence store and does not change `BASKOV_RECOMMENDATION_FEEDBACK_V2`.

Track affinity contributes the strongest share, artist affinity a medium share, and tag affinity a smaller generalized share. Negative feedback uses negative weights, so disliked regions can reduce cosine similarity rather than merely fail to add a bonus.

## Ranking safety

Vector similarity is bounded to `[-1, 1]` and receives a bounded strategy-dependent weight. Empty or low-confidence taste vectors contribute zero or very little.

Policy order stays authoritative:

1. recent-track rejection;
2. `discovery` hard novelty;
3. artist cooldown / existing base score;
4. explainable exact track/artist/tag affinity;
5. vector cosine contribution;
6. exploration bonus;
7. final score clamp.

Embeddings cannot resurrect a known/recent track rejected earlier in the pipeline.

## Explainability

`/radio model` shows:

- feedback evidence/confidence;
- embedding provider (`feature-hash-v1`);
- dimensions (`64D`);
- contributing feature count;
- vector confidence;
- existing artist/tag affinities and exploration rate.

`/radio why` includes vector similarity/confidence only when the vector contribution is material.

## Future provider path

A later release can implement `RecommendationEmbeddingProvider` with semantic embeddings (local model or bounded external service). The rest of the ranker and the playback pipeline do not need to know how the vector was produced.
