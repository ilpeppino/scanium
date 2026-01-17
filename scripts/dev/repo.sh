***REMOVED***!/usr/bin/env bash
***REMOVED*** Repo Switcher (portable: macOS Bash 3.2+, Linux, Termux)
***REMOVED*** - Scans /Users/family/dev for git repos whose folder name starts with "scanium"
***REMOVED*** - Interactive TUI (arrow keys) if fzf is available; otherwise select menu
***REMOVED*** - main branch always first (if present)
***REMOVED*** - If worktree is dirty: show uncommitted files, then ask commit/stash/discard/abort
***REMOVED*** - Once a branch is selected (and handled), the script EXITS (main goal)

set -euo pipefail
IFS=$'\n\t'

BASE_DIR="/Users/family/dev"
DEFAULT_REMOTE="origin"
FZF_HEIGHT="${FZF_HEIGHT:-60%}"

***REMOVED*** ---------- UI / Colors ----------
if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'
  C_DIM=$'\033[2m'
  C_BOLD=$'\033[1m'
  C_RED=$'\033[31m'
  C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'
  C_BLUE=$'\033[34m'
  C_CYAN=$'\033[36m'
else
  C_RESET="" C_DIM="" C_BOLD="" C_RED="" C_GREEN="" C_YELLOW="" C_BLUE="" C_CYAN=""
fi

hr() { printf "%s\n" "────────────────────────────────────────────────────────"; }
title() { printf "%s%s%s\n" "$C_BOLD" "$1" "$C_RESET"; }
info() { printf "%s[INFO]%s %s\n" "$C_CYAN" "$C_RESET" "$*"; }
ok()   { printf "%s[OK]%s   %s\n" "$C_GREEN" "$C_RESET" "$*"; }
warn() { printf "%s[WARN]%s %s\n" "$C_YELLOW" "$C_RESET" "$*"; }
err()  { printf "%s[ERR]%s  %s\n" "$C_RED" "$C_RESET" "$*" >&2; }

clear_screen() { printf "\033c" || true; }

need_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || { err "Missing required command: $cmd"; exit 1; }
}

***REMOVED*** ---------- Selection helpers ----------
has_fzf() { command -v fzf >/dev/null 2>&1; }

choose_from_list() {
  ***REMOVED*** Usage: choose_from_list "Prompt" options...
  local prompt="$1"; shift
  local options=("$@")

  [[ ${***REMOVED***options[@]} -gt 0 ]] || return 1

  if has_fzf; then
    local selection=""
    selection=$(
      printf '%s\n' "${options[@]}" | fzf \
        --prompt "${prompt} " \
        --height "${FZF_HEIGHT}" \
        --layout=reverse \
        --border \
        --info=inline \
        --no-multi
    ) || true
    [[ -n "$selection" ]] || return 1
    printf '%s' "$selection"
    return 0
  fi

  echo "$prompt" >&2
  local opt
  select opt in "${options[@]}"; do
    if [[ -n "${opt:-}" ]]; then
      printf '%s' "$opt"
      return 0
    fi
    echo "Invalid selection." >&2
  done
}

confirm_danger() {
  local message="$1"
  local token="$2"
  printf "%s%s%s\n" "$C_YELLOW" "$message" "$C_RESET" >&2
  read -r -p "Type '${token}' to confirm: " confirm
  [[ "$confirm" == "$token" ]]
}

***REMOVED*** ---------- Git helpers ----------
is_git_repo() {
  local dir="$1"
  git -C "$dir" rev-parse --is-inside-work-tree >/dev/null 2>&1
}

get_top_level() {
  local dir="$1"
  git -C "$dir" rev-parse --show-toplevel 2>/dev/null || true
}

pick_remote() {
  local repo_path="$1"
  local remotes=()
  while IFS= read -r r; do
    [[ -n "$r" ]] && remotes+=("$r")
  done < <(git -C "$repo_path" remote || true)

  if [[ ${***REMOVED***remotes[@]} -eq 0 ]]; then
    err "No git remotes found in: $repo_path"
    return 1
  fi

  local remote="${remotes[0]}"
  local r
  for r in "${remotes[@]}"; do
    if [[ "$r" == "$DEFAULT_REMOTE" ]]; then
      remote="$DEFAULT_REMOTE"
      break
    fi
  done
  printf '%s' "$remote"
}

list_remote_branches_main_first() {
  local remote="$1"

  local raw=()
  while IFS= read -r b; do
    [[ -n "$b" ]] && raw+=("$b")
  done < <(git ls-remote --heads "$remote" | awk '{print $2}' | sed 's***REMOVED***refs/heads/***REMOVED******REMOVED***' || true)

  [[ ${***REMOVED***raw[@]} -gt 0 ]] || return 1

  local has_main=false
  local rest=()
  local b
  for b in "${raw[@]}"; do
    if [[ "$b" == "main" ]]; then
      has_main=true
    else
      rest+=("$b")
    fi
  done

  if [[ ${***REMOVED***rest[@]} -gt 0 ]]; then
    IFS=$'\n' rest=($(printf "%s\n" "${rest[@]}" | sort))
    unset IFS
  fi

  $has_main && printf "%s\n" "main"
  [[ ${***REMOVED***rest[@]} -gt 0 ]] && printf "%s\n" "${rest[@]}"
}

ensure_branch_checked_out() {
  local remote="$1"
  local branch="$2"

  if git show-ref --verify --quiet "refs/heads/${branch}"; then
    git checkout "$branch" >/dev/null
  else
    git checkout -b "$branch" --track "${remote}/${branch}" >/dev/null
  fi

  if ! git rev-parse --abbrev-ref --symbolic-full-name "@{u}" >/dev/null 2>&1; then
    git branch --set-upstream-to "${remote}/${branch}" "${branch}" >/dev/null 2>&1 || true
  fi
}

pull_ff_only() { git pull --ff-only; }

working_tree_clean() { [[ -z "$(git status --porcelain)" ]]; }

print_dirty_files() {
  ***REMOVED*** Show a clean list of uncommitted paths (staged + unstaged + untracked)
  ***REMOVED*** Using porcelain v1 to stay compatible.
  local lines=()
  while IFS= read -r l; do
    [[ -n "$l" ]] && lines+=("$l")
  done < <(git status --porcelain)

  if [[ ${***REMOVED***lines[@]} -eq 0 ]]; then
    return 0
  fi

  echo
  title "Uncommitted files"
  hr
  local l
  for l in "${lines[@]}"; do
    ***REMOVED*** porcelain format: XY <path> OR ?? <path>
    ***REMOVED*** strip first 3 chars (XY + space) -> path-ish
    local path="${l:3}"
    local code="${l:0:2}"
    printf "%s- [%s]%s %s\n" "$C_DIM" "$code" "$C_RESET" "$path"
  done
  hr
  echo
}

***REMOVED*** ---------- Repo scanning ----------
scan_repos() {
  local repos=()
  local line top
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    [[ "$line" == *"/.git/"* ]] && continue
    top="$(get_top_level "$line")"
    if [[ -n "$top" && "$top" == "$line" ]]; then
      repos+=("$line")
    fi
  done < <(find "$BASE_DIR" -type d -name 'scanium*' -mindepth 1 2>/dev/null | sort)

  [[ ${***REMOVED***repos[@]} -gt 0 ]] || return 1
  printf "%s\n" "${repos[@]}"
}

***REMOVED*** ---------- Main ----------
main() {
  need_cmd git

  clear_screen
  title "Repo Switcher"
  printf "%sScanning for git repos under:%s %s\n" "$C_DIM" "$C_RESET" "$BASE_DIR"
  hr
  if ! has_fzf; then
    warn "Tip: install 'fzf' for arrow-key selection."
  fi
  echo

  local repos=()
  while IFS= read -r rp; do
    repos+=("$rp")
  done < <(scan_repos || true)

  if [[ ${***REMOVED***repos[@]} -eq 0 ]]; then
    err "No directories named 'scanium*' found under: $BASE_DIR"
    exit 1
  fi

  local repo_path
  repo_path=$(choose_from_list "Select repo" "${repos[@]}") || {
    err "No repo selected"
    exit 1
  }

  if ! is_git_repo "$repo_path"; then
    err "Not a git repo: $repo_path"
    exit 1
  fi

  cd "$repo_path"

  local remote
  remote="$(pick_remote "$repo_path")" || exit 1

  local branches=()
  while IFS= read -r b; do
    [[ -n "$b" ]] && branches+=("$b")
  done < <(list_remote_branches_main_first "$remote" || true)

  if [[ ${***REMOVED***branches[@]} -eq 0 ]]; then
    err "Remote '$remote' has no branches or is unreachable."
    exit 1
  fi

  local branch
  branch=$(choose_from_list "Select branch (${remote})" "${branches[@]}") || {
    err "No branch selected"
    exit 1
  }

  info "Repo:   $repo_path"
  info "Remote: $remote"
  info "Branch: $branch"
  echo

  ensure_branch_checked_out "$remote" "$branch"
  ok "Checked out: $(git rev-parse --abbrev-ref HEAD)"
  echo

  local last_action=""
  if working_tree_clean; then
    info "Working tree clean. Pulling (ff-only)..."
    if pull_ff_only; then
      last_action="pulled (clean tree)"
      ok "Pull complete."
    else
      last_action="pull failed"
      warn "Pull failed. Resolve manually."
    fi
  else
    warn "Working tree is dirty."
    print_dirty_files

    local actions=(
      "Commit changes"
      "Stash changes"
      "Discard changes and align with remote (DANGEROUS)"
      "Abort (do nothing)"
    )
    local action
    action=$(choose_from_list "Choose action" "${actions[@]}") || {
      err "No action selected"
      exit 1
    }

    case "$action" in
      "Commit changes")
        local msg=""
        while [[ -z "$msg" ]]; do
          read -r -p "Commit message: " msg
        done
        git add -A
        git commit -m "$msg"
        pull_ff_only || true
        last_action="committed changes and pulled"
        ok "Committed + pulled."
        ;;
      "Stash changes")
        git stash push -u -m "repo-switcher $(date +%Y-%m-%d)"
        pull_ff_only || true
        last_action="stashed changes and pulled"
        ok "Stashed + pulled."
        ;;
      "Discard changes and align with remote (DANGEROUS)")
        if confirm_danger "This will PERMANENTLY delete local changes (reset --hard + clean -fd)." "discard"; then
          git reset --hard
          git clean -fd
          pull_ff_only || true
          last_action="discarded changes and pulled"
          ok "Discarded + pulled."
        else
          warn "Discard cancelled."
          last_action="discard cancelled"
        fi
        ;;
      "Abort (do nothing)")
        warn "Aborted."
        last_action="aborted"
        ;;
    esac
  fi

  echo
  hr
  printf "%sRepo:%s   %s\n" "$C_DIM" "$C_RESET" "$repo_path"
  printf "%sBranch:%s %s\n" "$C_DIM" "$C_RESET" "$(git rev-parse --abbrev-ref HEAD)"
  printf "%sAction:%s %s\n" "$C_DIM" "$C_RESET" "${last_action:-none}"
  hr

  ***REMOVED*** EXIT as requested (main goal)
  exit 0
}

main