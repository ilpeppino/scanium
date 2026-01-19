***REMOVED*** Local Tooling

***REMOVED******REMOVED*** repo command

Install (from this repo):

```bash
./scripts/dev/install-repo-command.sh
```

Use:

```bash
repo
```

Optional for best UX (arrow-key selection):

```bash
brew install fzf
```

What the actions do when your working tree is dirty:

- Commit changes: `git add -A`, prompt for a commit message, `git commit`, then
  `git pull --ff-only`.
- Stash changes: `git stash push -u`, then `git pull --ff-only`.
- Discard changes and align with remote: confirm, then `git reset --hard`, `git clean -fd`, and
  `git pull --ff-only`.
- Abort: exit without making changes.
