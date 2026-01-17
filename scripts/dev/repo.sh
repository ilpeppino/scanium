***REMOVED***!/usr/bin/env bash
***REMOVED*** Repo Switcher (portable: macOS Bash 3.2+, Linux, Termux)
***REMOVED*** - Scans /Users/family/dev for git repos whose folder name starts with "scanium"
***REMOVED*** - Interactive TUI (arrow keys) if fzf is available; otherwise select menu
***REMOVED*** - Lets you pick remote branch (main always first if present)
***REMOVED*** - Handles dirty working tree (commit/stash/discard/abort)
***REMOVED*** - Stays in a loop until you quit

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
title() {
  printf "%s%s%s\n" "$C_BOLD" "$1" "$C_RESET"
}
info() { printf "%s[INFO]%s %s\n" "$C_CYAN" "$C_RESET" "$*"; }
ok()   { printf "%s[OK]%s   %s\n" "$C_GREEN" "$C_RESET" "$*"; }
warn() { printf "%s[WARN]%s %s\n" "$C_YELLOW" "$C_RESET" "$*"; }
err()  { printf "%s[ERR]%s  %s\n" "$C_RED" "$C_RESET" "$*" >&2; }

pause_hint() {
  printf "%s%s%s\n" "$C_DIM" "$1" "$C_RESET"
}

clear_screen() {
  ***REMOVED*** shellcheck disable=SC2034
  printf "\033c" || true
}

need_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || { err "Missing required command: $cmd"; return 1; }
}

***REMOVED*** ---------- Selection helpers ----------
has_fzf() { command -v fzf >/dev/null 2>&1; }

choose_from_list() {
  ***REMOVED*** Usage: choose_from_list "Prompt" options...
  local prompt="$1"; shift
  local options=("$@")

  if [[ ${***REMOVED***options[@]} -eq 0 ]]; then
    return 1
  fi

  if has_fzf; then
    ***REMOVED*** Arrow-key selection via fzf
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

  ***REMOVED*** Fallback: bash select
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

choose_action() {
  local prompt="$1"; shift
  local actions=("$@")
  choose_from_list "$prompt" "${actions[@]}"
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

  ***REMOVED*** Prefer origin if present
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

  ***REMOVED*** Grab remote branch names
  local raw=()
  while IFS= read -r b; do
    [[ -n "$b" ]] && raw+=("$b")
  done < <(git ls-remote --heads "$remote" | awk '{print $2}' | sed 's***REMOVED***refs/heads/***REMOVED******REMOVED***' || true)

  if [[ ${***REMOVED***raw[@]} -eq 0 ]]; then
    return 1
  fi

  ***REMOVED*** Ensure main first if present, keep the rest sorted (stable)
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

  ***REMOVED*** Sort rest (portable)
  if [[ ${***REMOVED***rest[@]} -gt 0 ]]; then
    IFS=$'\n' rest=($(printf "%s\n" "${rest[@]}" | sort))
    unset IFS
  fi

  if $has_main; then
    printf "%s\n" "main"
  fi
  if [[ ${***REMOVED***rest[@]} -gt 0 ]]; then
    printf "%s\n" "${rest[@]}"
  fi
}

ensure_branch_checked_out() {
  local remote="$1"
  local branch="$2"

  if git show-ref --verify --quiet "refs/heads/${branch}"; then
    git checkout "$branch" >/dev/null
  else
    git checkout -b "$branch" --track "${remote}/${branch}" >/dev/null
  fi

  ***REMOVED*** Ensure upstream
  if ! git rev-parse --abbrev-ref --symbolic-full-name "@{u}" >/dev/null 2>&1; then
    git branch --set-upstream-to "${remote}/${branch}" "${branch}" >/dev/null 2>&1 || true
  fi
}

pull_ff_only() {
  git pull --ff-only
}

working_tree_clean() {
  [[ -z "$(git status --porcelain)" ]]
}

***REMOVED*** ---------- Repo scanning ----------
scan_repos() {
  local repos=()

  ***REMOVED*** Find directories starting with scanium* under BASE_DIR
  ***REMOVED*** and keep only those that are top-level git repos.
  local line top
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    ***REMOVED*** Skip nested .git paths (defensive)
    if [[ "$line" == *"/.git/"* ]]; then
      continue
    fi
    top="$(get_top_level "$line")"
    if [[ -n "$top" && "$top" == "$line" ]]; then
      repos+=("$line")
    fi
  done < <(find "$BASE_DIR" -type d -name 'scanium*' -mindepth 1 2>/dev/null | sort)

  if [[ ${***REMOVED***repos[@]} -eq 0 ]]; then
    return 1
  fi

  printf "%s\n" "${repos[@]}"
}

***REMOVED*** ---------- Main Loop ----------
main_loop() {
  need_cmd git

  while true; do
    clear_screen
    title "Repo Switcher"
    printf "%sScanning for git repos under:%s %s\n" "$C_DIM" "$C_RESET" "$BASE_DIR"
    hr
    pause_hint "Tip: install 'fzf' for arrow-key selection. Press Ctrl+C anytime to exit."
    echo

    local repos=()
    while IFS= read -r rp; do
      repos+=("$rp")
    done < <(scan_repos || true)

    if [[ ${***REMOVED***repos[@]} -eq 0 ]]; then
      err "No directories named 'scanium*' found under: $BASE_DIR"
      echo
      pause_hint "Fix: Ensure repos are under $BASE_DIR and are valid git repos."
      read -r -p "Press Enter to rescan, or Ctrl+C to quit..." _
      continue
    fi

    local repo_path
    repo_path=$(choose_from_list "Select repo (or ESC to cancel)" "${repos[@]}") || {
      warn "No repo selected."
      read -r -p "Press Enter to continue..." _
      continue
    }

    if ! is_git_repo "$repo_path"; then
      err "Not a git repo: $repo_path"
      read -r -p "Press Enter to continue..." _
      continue
    fi

    cd "$repo_path"

    local remote
    remote="$(pick_remote "$repo_path")" || {
      read -r -p "Press Enter to continue..." _
      continue
    }

    info "Repo: $repo_path"
    info "Remote: $remote"
    echo

    ***REMOVED*** List branches (main first)
    local branches=()
    while IFS= read -r b; do
      [[ -n "$b" ]] && branches+=("$b")
    done < <(list_remote_branches_main_first "$remote" || true)

    if [[ ${***REMOVED***branches[@]} -eq 0 ]]; then
      err "Remote '$remote' has no branches or is unreachable."
      read -r -p "Press Enter to continue..." _
      continue
    fi

    local branch
    branch=$(choose_from_list "Select branch (${remote})" "${branches[@]}") || {
      warn "No branch selected."
      read -r -p "Press Enter to continue..." _
      continue
    }

    info "Checking out: $branch"
    ensure_branch_checked_out "$remote" "$branch"
    ok "On branch: $(git rev-parse --abbrev-ref HEAD)"
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
      echo
      local actions=(
        "Commit changes"
        "Stash changes"
        "Discard changes and align with remote (DANGEROUS)"
        "Abort (do nothing)"
      )

      local action
      action=$(choose_action "Choose action" "${actions[@]}") || {
        warn "No action selected."
        read -r -p "Press Enter to continue..." _
        continue
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
    echo

    local post_actions=(
      "Stay here (do nothing)"
      "Switch repo/branch again"
      "Quit"
    )

    local post
    post=$(choose_action "Next" "${post_actions[@]}") || post="Stay here (do nothing)"

    case "$post" in
      "Stay here (do nothing)")
        ***REMOVED*** Return to shell in the selected repo/branch but keep interface looping
        read -r -p "Press Enter to return to Repo Switcher menu..." _
        ;;
      "Switch repo/branch again")
        ***REMOVED*** Just loop
        ;;
      "Quit")
        clear_screen
        exit 0
        ;;
    esac
  done
}

main_loop