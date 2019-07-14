#!/bin/bash

set -ex

cd cli/packages/prisma-datamodel
yarn
yarn test
yarn build
cd ../prisma-yml
yarn
yarn test
yarn build
cd ../prisma-db-introspection
yarn
yarn test
yarn build
cd ../prisma-generate-schema
yarn
yarn test
yarn build
cd ../prisma-client-lib
yarn
yarn build
yarn test
cd ../prisma-cli-engine
yarn
yarn test
yarn build
cd ../prisma-cli-core
yarn
yarn build
yarn test

