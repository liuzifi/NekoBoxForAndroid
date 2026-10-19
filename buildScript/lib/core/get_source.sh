#!/bin/bash
set -e

source "buildScript/init/env.sh"
ENV_NB4A=1
source "buildScript/lib/core/get_source_env.sh"
PATCH_DIR="$(pwd)/libcore/patches"
pushd ..

####

if [ ! -d "sing-box" ]; then
  git clone --no-checkout https://github.com/starifly/sing-box.git
fi
pushd sing-box
# --force drops a previously applied patch so the apply below is idempotent
git checkout --force "$COMMIT_SING_BOX"
# Backport of ech.query_server_name (sing-box 1.13): lets ECH look up the HTTPS record of the
# "ech=<domain>+<doh>" domain (e.g. cloudflare-ech.com) instead of the SNI.
git apply "$PATCH_DIR/sing-box-ech-query-server-name.patch"
popd

####

if [ ! -d "libneko" ]; then
  git clone --no-checkout https://github.com/starifly/libneko.git
fi
pushd libneko
git checkout "$COMMIT_LIBNEKO"
popd

####

popd
