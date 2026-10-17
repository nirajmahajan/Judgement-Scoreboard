#!/usr/bin/env bash
set -euo pipefail
mode="${1:-verify}"
[[ "$mode" == verify || "$mode" == record ]] || { echo 'Use verify or record'; exit 2; }
if [[ "$mode" == record && "${CI:-false}" == true ]]; then
  echo 'CI verifies reviewed goldens; it does not record baselines.'; exit 1
fi
image=judgement-screenshots:local
docker build --platform linux/amd64 --file tools/ci/Dockerfile.screenshots --tag "$image" .
if [[ "$mode" == verify ]]; then
  [[ -n "$(find app/src/test/screenshots -name '*.png' -print -quit)" ]] || { echo 'No reviewed screenshot goldens found'; exit 1; }
  task=verifyRoborazziDebug
else
  task=recordRoborazziDebug
fi
docker run --platform linux/amd64 --rm --env CI="${CI:-false}" --volume "$PWD:/workspace" --volume judgement-gradle-linux-amd64:/root/.gradle --volume judgement-m2-linux-amd64:/root/.m2 "$image" ./gradlew --no-daemon --no-watch-fs --dependency-verification=strict "$task"
