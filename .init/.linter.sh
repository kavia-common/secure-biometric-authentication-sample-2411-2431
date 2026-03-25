#!/bin/bash
cd /home/kavia/workspace/code-generation/secure-biometric-authentication-sample-2411-2431/android_kotlin_frontend
./gradlew lint
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

