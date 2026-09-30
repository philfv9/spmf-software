# cgSpan documentation — updated sections

These are the sections of the "Mining Frequent Closed Subgraphs Using the cgSpan
Algorithm" page that change now that the `closedByThreshold` parameter exists and
defaults to `true`. Unchanged sections of the page are omitted.

---

## What is a "closed" subgraph? (revised concept section)

cgSpan is a closed frequent subgraph mining algorithm. Two different notions of
"closed" are relevant, and cgSpan can output either one:

**1. cgSpan closure (equivalent occurrence).** This is the notion cgSpan uses
internally. A frequent subgraph is *cgSpan-closed* if it has no proper supergraph
that has an *equivalent occurrence* — informally, a supergraph that always occurs
exactly where the subgraph occurs (each occurrence of the subgraph extends to the
supergraph in the same way). This is the closure definition from the original
cgSpan paper.

**2. Threshold-based closure.** A frequent subgraph is *threshold-closed* if it
has no proper supergraph with the *same value of the frequency measure*:
- For **transaction databases (support)** this is the CloseGraph definition: no
  proper supergraph has the same support.
- For **a single large graph (MNI)** this is the CloGraMi/GraMi definition: no
  proper supergraph has the same MNI (Minimum Node Image) value.

Equivalent occurrence is a strictly stronger condition than "equal support/MNI":
if a supergraph occurs everywhere the subgraph does, it necessarily has the same
support (and the same MNI). Therefore the set of threshold-closed subgraphs is a
**subset** of the set of cgSpan-closed subgraphs. In other words, filtering by the
threshold measure removes some subgraphs that cgSpan considers closed but that
still have a proper supergraph of identical support/MNI.

The `closedByThreshold` parameter (see Optional parameters) selects which of these
two result sets is produced.

---

## Optional parameters (add this entry)

**`closedByThreshold`** (boolean, default `true`)

Controls which definition of "closed" is used to produce the output:

- `true` (default) — only **threshold-closed** subgraphs are output: for the
  support version (`AlgoCGSPANSupport`), a subgraph is kept only if no proper
  supergraph has the same support (CloseGraph); for the MNI version
  (`AlgoCGSPANMNI`), a subgraph is kept only if no proper supergraph has the same
  MNI (CloGraMi). This is the most compact result set.
- `false` — all **cgSpan-closed** subgraphs (equivalent-occurrence closure) are
  output. When `false`, the extra check for threshold closure is skipped, so this
  is also slightly faster.

Because threshold-closed ⊆ cgSpan-closed, the `true` setting always returns the
same subgraphs as `false` or fewer — never more.

This parameter is exposed as the optional **"Closed By Threshold"** parameter in
both the graphical user interface and the command line (the 8th parameter of the
CGSPANMNI and CGSPANSupport algorithms), and can also be set from the source code
via the method `setClosedByThreshold(boolean)` on `AlgoCGSPANMNI` /
`AlgoCGSPANSupport` (e.g. `algo.setClosedByThreshold(false);`). If it is not
provided, it defaults to `true`.

---

## Example 3 — single large graph, MNI (revised output)

> This example replaces the previous single-large-graph MNI example, whose output
> listed a single 2-edge subgraph. That output was too small by one edge (an
> off-by-one in the size guard truncated results one edge short of the maximum).
> With the fix, and with `minMNI = 2` / `maxNumberOfEdges = 4`, the example now
> correctly returns two 3-edge closed subgraphs, both with MNI = 2. This output
> is the same whether `closedByThreshold` is `true` (default) or `false`, because
> for this input the two subgraphs are both threshold-closed and cgSpan-closed.

Input (`contextSingleGraph.txt`):

```
t # 0
v 1 10
v 2 10
v 3 11
v 4 11
e 1 2 21
e 1 3 20
e 2 3 20
e 2 4 20
e 3 4 22
```

Output (`outputGraphIds = true`):

```
t # 0 * 2
v 0 10
v 1 11
v 2 11
v 3 10
e 0 1 20
e 1 2 22
e 2 3 20
x 0

t # 1 * 2
v 0 10
v 1 11
v 2 11
v 3 10
e 0 1 20
e 1 2 22
e 0 3 21
x 0
```

Each block is one closed subgraph. `t # <id> * <MNI>` gives the subgraph id and
its MNI value (2 for both). The `v` / `e` lines describe vertices and edges
(`v <id> <label>`, `e <src> <dst> <label>`). The `x` line lists the ids of the
database graphs containing the subgraph (here only graph `0`, the single input
graph).

With debug information enabled (`setDebugMode(true)`), each header also shows the
number of occurrences after a second `*`, and the `x` line shows the occurrence
count per graph:

```
t # 0 * 2 * 2
v 0 10
v 1 11
v 2 11
v 3 10
e 0 1 20
e 1 2 22
e 2 3 20
x 0x2

t # 1 * 2 * 3
v 0 10
v 1 11
v 2 11
v 3 10
e 0 1 20
e 1 2 22
e 0 3 21
x 0x3
```
