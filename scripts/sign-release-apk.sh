#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: REPRODROID_SIGNING_KEYSTORE=/path/to/release.p12 REPRODROID_SIGNING_PASSWORD_FILE=/path/to/passphrase $0 INPUT_UNSIGNED_APK OUTPUT_SIGNED_APK OUTPUT_PROVENANCE" >&2
}

if [ "$#" -ne 3 ]; then
  usage
  exit 64
fi

: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
: "${REPRODROID_SIGNING_KEYSTORE:?REPRODROID_SIGNING_KEYSTORE is required}"
: "${REPRODROID_SIGNING_PASSWORD_FILE:?REPRODROID_SIGNING_PASSWORD_FILE is required}"

readonly input_apk="$(realpath "$1")"
readonly output_apk="$(realpath -m "$2")"
readonly output_provenance="$(realpath -m "$3")"
readonly signing_alias="${REPRODROID_SIGNING_ALIAS:-reprodroid-release}"

for required_file in "$input_apk" "$REPRODROID_SIGNING_KEYSTORE" "$REPRODROID_SIGNING_PASSWORD_FILE"; do
  if [ ! -f "$required_file" ] || [ -L "$required_file" ]; then
    echo "Refusing non-regular or symlink input: $required_file" >&2
    exit 66
  fi
done
if [ "$input_apk" = "$output_apk" ]; then
  echo "Input and output APK paths must differ." >&2
  exit 64
fi
for new_file in "$output_apk" "$output_provenance"; do
  if [ -e "$new_file" ] || [ -L "$new_file" ]; then
    echo "Refusing to replace an existing output: $new_file" >&2
    exit 73
  fi
  if [ ! -d "$(dirname "$new_file")" ]; then
    echo "Output directory does not exist: $(dirname "$new_file")" >&2
    exit 73
  fi
done

readonly script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly source_commit="$(git -C "$script_dir/.." rev-parse HEAD)"
if [ -n "$(git -C "$script_dir/.." status --porcelain --untracked-files=normal)" ]; then
  echo "Refusing release signing from a dirty source checkout." >&2
  exit 65
fi
if [ -n "${REPRODROID_EXPECTED_SOURCE_COMMIT:-}" ] && [ "$source_commit" != "$REPRODROID_EXPECTED_SOURCE_COMMIT" ]; then
  echo "Source commit does not match REPRODROID_EXPECTED_SOURCE_COMMIT." >&2
  exit 65
fi

readonly build_tools_version="${REPRODROID_ANDROID_BUILD_TOOLS_VERSION:-36.0.0}"
if [[ ! "$build_tools_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "REPRODROID_ANDROID_BUILD_TOOLS_VERSION must be an exact numeric version." >&2
  exit 64
fi
readonly build_tools_dir="$ANDROID_HOME/build-tools/$build_tools_version"
readonly zipalign="$build_tools_dir/zipalign"
readonly apksigner="$build_tools_dir/apksigner"
if [ ! -d "$build_tools_dir" ] || [ -L "$build_tools_dir" ] || [ ! -x "$zipalign" ] || [ ! -x "$apksigner" ]; then
  echo "Exact Android build-tools $build_tools_version with zipalign and apksigner is required." >&2
  exit 69
fi

readonly output_dir="$(dirname "$output_apk")"
readonly temporary_dir="$(mktemp -d "$output_dir/.reprodroid-sign.XXXXXX")"
cleanup() {
  rm -rf -- "$temporary_dir"
}
trap cleanup EXIT INT TERM

readonly aligned_apk="$temporary_dir/aligned.apk"
readonly signed_apk="$temporary_dir/signed.apk"
readonly verification="$temporary_dir/apksigner-verification.txt"
readonly provenance="$temporary_dir/provenance.txt"

"$zipalign" -f -p 4 "$input_apk" "$aligned_apk"
"$apksigner" sign \
  --ks "$REPRODROID_SIGNING_KEYSTORE" \
  --ks-type PKCS12 \
  --ks-key-alias "$signing_alias" \
  --ks-pass "file:$REPRODROID_SIGNING_PASSWORD_FILE" \
  --out "$signed_apk" \
  "$aligned_apk"
"$zipalign" -c -p 4 "$signed_apk"
"$apksigner" verify --verbose --print-certs "$signed_apk" > "$verification"

actual_signer="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$verification" | head -n 1 | tr '[:lower:]' '[:upper:]')"
if [ -z "$actual_signer" ]; then
  echo "Unable to read signer SHA-256 from apksigner verification." >&2
  exit 65
fi
if [ -n "${REPRODROID_EXPECTED_SIGNER_SHA256:-}" ]; then
  expected_signer="$(printf '%s' "$REPRODROID_EXPECTED_SIGNER_SHA256" | tr -d ':' | tr '[:lower:]' '[:upper:]')"
  if [ "$actual_signer" != "$expected_signer" ]; then
    echo "Signer fingerprint does not match REPRODROID_EXPECTED_SIGNER_SHA256." >&2
    exit 65
  fi
fi

{
  printf 'format=reprodroid-apk-signing-provenance-v1\n'
  printf 'generated_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'source_commit=%s\n' "$source_commit"
  printf 'unsigned_apk_sha256=%s\n' "$(sha256sum "$input_apk" | awk '{print $1}')"
  printf 'aligned_unsigned_apk_sha256=%s\n' "$(sha256sum "$aligned_apk" | awk '{print $1}')"
  printf 'signed_apk_sha256=%s\n' "$(sha256sum "$signed_apk" | awk '{print $1}')"
  printf 'signer_certificate_sha256=%s\n' "$actual_signer"
  printf 'android_build_tools=%s\n' "$(basename "$build_tools_dir")"
  printf '\n[apksigner verification]\n'
  cat "$verification"
} > "$provenance"

# Hard links publish each completed file atomically and fail if a path appeared after validation.
ln "$signed_apk" "$output_apk"
if ! ln "$provenance" "$output_provenance"; then
  rm -f -- "$output_apk"
  exit 73
fi
chmod 0644 "$output_apk" "$output_provenance"

echo "Signed APK: $output_apk"
echo "Provenance: $output_provenance"
