#! /bin/bash
# Builds hq-shared-lib and packs it as dist/hq-shared-lib/hq-shared-lib-<version>.tgz,
# then copies the .tgz into every consuming app and updates each app's package-lock.json hash (scripts/sync-consumers.mjs).
set -e
npm install
if npx ng build ; then
   cd dist/hq-shared-lib/
   rm -f hq-shared-lib*.tgz
   npm pack
   cd ../..
   node scripts/sync-consumers.mjs
else
   echo "Error in compile"
   exit 1
fi
