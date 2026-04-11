# 🐛 Introduced Bug — Reference Sheet
> **Instructor / reviewer eyes only.** Do not share with the developer completing the exercise.

---

## File changed

`app/api/routes/articles/articles_resource.py`

---

## Code before the change

```python
# app/api/routes/articles/articles_resource.py  (lines ~43-47)

    return ListOfArticlesInResponse(
        articles=articles_for_response,
        articles_count=len(articles),
    )
```

---

## Code after the change (buggy version)

```python
# app/api/routes/articles/articles_resource.py  (lines ~43-47)

    return ListOfArticlesInResponse(
        articles=articles_for_response,
        articles_count=len(articles_for_response) + articles_filters.offset,
    )
```

---

## Why this bug is subtle

| Property | Detail |
|---|---|
| **Crash?** | No — the application runs normally in all cases. |
| **First-page requests** | `offset` defaults to `0`, so `len(...) + 0` equals the correct value. Every smoke test and first-load request passes. |
| **Paginated requests** | As soon as `offset > 0` the count is inflated by exactly `offset` units, making clients believe more articles remain than actually do. A client fetching page 2 (`offset=20`) with only 3 results will receive `articles_count: 23` while `articles` contains only 3 items. |
| **Why it survives code review** | `len(articles_for_response)` looks like a reasonable refactor of `len(articles)` (the two lists are identical in length). The `+ articles_filters.offset` reads as if it is "correcting" for the offset — plausible to a reviewer unfamiliar with the Conduit spec, where `articlesCount` must be the count of the *current page*, not a cumulative total. |
| **Affected path** | Any call to `GET /api/articles` with `offset > 0`. Single-article lookups, feed, create, update, delete — all unaffected. |
