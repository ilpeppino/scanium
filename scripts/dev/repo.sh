***REMOVED***!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

BASE_DIR="/Users/family/dev"

err() {
  echo "repo: $*" >&2
}

choose_from_list() {
  local prompt="$1"
  shift
  local options=("$@")

  if command -v fzf >/dev/null 2>&1; then
    local selection
    selection=$(printf '%s\n' "${options[@]}" | fzf --prompt "${prompt} " --height 40% --reverse) || true
    if [[ -z "${selection}" ]]; then
      return 1
    fi
    printf '%s' "${selection}"
    return 0
  fi

  echo "${prompt}" >&2
  local opt
  select opt in "${options[@]}"; do
    if [[ -n "${opt}" ]]; then
      printf '%s' "${opt}"
      return 0
    fi
    echo "Invalid selection." >&2
  done
}

repos=()
while IFS= read -r line; do
  if [[ "${line}" == *"/.git/"* ]]; then
    continue
  fi
  if top=$(git -C "${line}" rev-parse --show-toplevel 2>/dev/null); then
    if [[ "${top}" == "${line}" ]]; then
      repos+=("${line}")
    fi
  fi
done < <(find "${BASE_DIR}" -type d -name 'scanium*' -mindepth 1 2>/dev/null | sort)

if [[ ${***REMOVED***repos[@]} -eq 0 ]]; then
  err "no directories named 'scanium*' found under ${BASE_DIR}"
  exit 1
fi

repo_path=$(choose_from_list "Select repo" "${repos[@]}") || {
  err "no repo selected"
  exit 1
}

if ! git -C "${repo_path}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  err "${repo_path} is not a git repo"
  exit 1
fi

cd "${repo_path}"

remotes=()
while IFS= read -r line; do
  remotes+=("$line")
done < <(git remote)
if [[ ${***REMOVED***remotes[@]} -eq 0 ]]; then
  err "no git remotes found in ${repo_path}"
  exit 1
fi

remote="${remotes[0]}"
for r in "${remotes[@]}"; do
  if [[ "${r}" == "origin" ]]; then
    remote="origin"
    break
  fi
done

branches=()
while IFS= read -r line; do
  branches+=("$line")
done < <(git ls-remote --heads "${remote}" | awk '{print $2}' | sed 's***REMOVED***refs/heads/***REMOVED******REMOVED***' | sort)
if [[ ${***REMOVED***branches[@]} -eq 0 ]]; then
  err "remote ${remote} has no branches"
  exit 1
fi

branch=$(choose_from_list "Select branch (${remote})" "${branches[@]}") || {
  err "no branch selected"
  exit 1
}

if git show-ref --verify --quiet "refs/heads/${branch}"; then
  git checkout "${branch}"
else
  git checkout -b "${branch}" --track "${remote}/${branch}"
fi

if ! git rev-parse --abbrev-ref --symbolic-full-name "@{u}" >/dev/null 2>&1; then
  git branch --set-upstream-to "${remote}/${branch}" "${branch}"
fi

last_action=""

if [[ -z "$(git status --porcelain)" ]]; then
  git pull --ff-only
  last_action="pulled (clean tree)"
else
  actions=(
    "Commit changes"
    "Stash changes"
    "Discard changes and align with remote"
    "Abort"
  )

  action=$(choose_from_list "Working tree dirty. Choose action" "${actions[@]}") || {
    err "no action selected"
    exit 1
  }

  case "${action}" in
    "Commit changes")
      msg=""
      while [[ -z "${msg}" ]]; do
        read -r -p "Commit message: " msg
      done
      git add -A
      git commit -m "${msg}"
      git pull --ff-only
      last_action="committed changes and pulled"
      ;;
    "Stash changes")
      git stash push -u -m "repo-switcher $(date +%Y-%m-%d)"
      git pull --ff-only
      last_action="stashed changes and pulled"
      ;;
    "Discard changes and align with remote")
      read -r -p "Type 'discard' to permanently drop local changes: " confirm
      if [[ "${confirm}" != "discard" ]]; then
        err "discard cancelled"
        exit 1
      fi
      git reset --hard
      git clean -fd
      git pull --ff-only
      last_action="discarded changes and pulled"
      ;;
    "Abort")
      err "aborted"
      exit 1
      ;;
  esac
fi

current_branch=$(git rev-parse --abbrev-ref HEAD)

cat <<SUMMARY
Repo: ${repo_path}
Branch: ${current_branch}
Action: ${last_action}
SUMMARY
