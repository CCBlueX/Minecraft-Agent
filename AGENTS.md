Orphan branch with its own history: never merge it into `main` or `main` into it.
`main`'s `.github/workflows/pages.yml` checks this branch out into `site-generator/`, copies each module's
`build/dokka/html` into one folder per module, runs `./gradlew run` here against the `main` checkout and deploys the
output with `actions/deploy-pages`. Changes to that workflow go to `main`; GitHub only runs workflows on the pushed branch.
Missing inputs (no SKILL, no `ai-usage/` files, no API) get a placeholder line, never a failure. No CSS on the pages.
