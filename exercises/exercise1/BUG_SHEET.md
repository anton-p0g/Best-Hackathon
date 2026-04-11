# BUG_SHEET.md
> **Instructor / reviewer reference only — do not share with the developer.**

---

## Section 1 — File changed

`backend/app/api/routes/items.py`

---

## Section 2 — Code before the change (original)

```python
# backend/app/api/routes/items.py  ~lines 40-44 (superuser branch)
# and ~lines 53-57 (regular-user branch)

    items_public = [ItemPublic.model_validate(item) for item in items]
    return ItemsPublic(data=items_public, count=count)
```

---

## Section 3 — Code after the change (buggy version)

```python
# backend/app/api/routes/items.py  ~lines 40-44 (superuser branch)
# and ~lines 53-57 (regular-user branch)

    items_public = [ItemPublic.model_validate(item) for item in items]
    return ItemsPublic(data=items_public, count=skip + len(items_public))
```

The single-line change replaces the database-derived `count` (total rows in the table
for this user) with `skip + len(items_public)` — a value that depends on the request's
pagination offset and the size of the current page, not on the true total.

---

## Section 4 — Why this bug is subtle

| Row label | Content |
|---|---|
| Crash? | No. The application runs normally and returns HTTP 200 for every request. |
| First affected path | `GET /items/?skip=1` (or any `skip > 0`). The returned `count` is wrong as soon as the caller pages past the first result. |
| Unaffected paths | `GET /items/` with default `skip=0` — when the result set is smaller than `limit`, `0 + len(items)` accidentally equals the true total, so the first page looks correct. Single-item lookup (`GET /items/{id}`), create, update, and delete are entirely unaffected. |
| Why it survives code review | `skip + len(items_public)` superficially resembles a "running position" or "cursor" calculation. A reviewer skimming for off-by-one errors in pagination logic might read it as intentional. The original `count` variable is still declared and used in the query block just above, so there is no unused-variable warning. |
| Scaling / compounding | The error grows linearly with `skip`: at `skip=50` with 10 items on the page, `count` returns 60 instead of (say) 200. A frontend that drives its "total pages" calculation from `count` will show progressively fewer pages the deeper the user scrolls — the UI shrinks the pagination controls in real time. |
| Why tests may not catch it | The default test fixtures likely call the endpoint with `skip=0`. Because the result is accidentally correct at that offset, all existing assertions on `count` pass. Only a test that explicitly uses `skip > 0` and cross-checks `count` against a known dataset size would expose the defect. |
