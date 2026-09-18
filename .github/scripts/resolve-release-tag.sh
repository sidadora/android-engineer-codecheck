#!/usr/bin/env bash
#
# 役割: 更新前後のコミットのversionNameを比較し、リリースタグが必要かを判定する。
# 引数: $1 更新前のコミットSHA (github.event.before) / $2 更新後のコミットSHA (github.sha)
# 出力: 標準出力に changed=true|false と、変更時のみ tag=v<versionName>。説明とエラーは標準エラー出力。
# 備考: タグの作成・pushは行わない。
set -euo pipefail

# versionNameは「独立した1行の固定文字列」で定義されている前提で取得する。
# 任意のGroovyは解析しない。記述形式を変える場合はこの処理も見直すこと。
extract_version_name() {
  local content="$1"
  local label="$2"
  local candidates line value

  # 候補は versionName= のような書き方も数え、定義漏れを見逃さない。
  candidates=$(printf '%s\n' "$content" \
    | grep -cE "^[[:space:]]*versionName([[:space:]]|=|\()" || true)

  if [ "$candidates" -eq 0 ]; then
    echo "${label}: versionName の定義が見つかりません。" >&2
    return 1
  fi
  if [ "$candidates" -gt 1 ]; then
    echo "${label}: versionName の定義候補が ${candidates} 件あります。1 件である必要があります。" >&2
    return 1
  fi

  line=$(printf '%s\n' "$content" \
    | grep -E "^[[:space:]]*versionName([[:space:]]|=|\()" | head -1)

  # 対応する形式は versionName "数値" の1行のみ。値は評価せず取り出す。
  value=$(printf '%s\n' "$line" \
    | sed -nE 's/^[[:space:]]*versionName[[:space:]]+"([0-9]+(\.[0-9]+)*)"[[:space:]]*$/\1/p')

  if [ -z "$value" ]; then
    echo "${label}: 未対応の記述です: $(printf '%s' "$line" | sed 's/^[[:space:]]*//')" >&2
    echo '対応する形式は versionName "1.1.0" の 1 行のみです。' >&2
    return 1
  fi

  printf '%s' "$value"
}

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <before-sha> <after-sha>" >&2
  exit 1
fi

before_sha="$1"
after_sha="$2"

# 比較元を取得できない場合は、バージョン変更なしとして扱わず失敗させる。
if [ -z "$before_sha" ] || [ "$before_sha" = "0000000000000000000000000000000000000000" ]; then
  echo "更新前のコミットを特定できません (before=${before_sha:-空})。" >&2
  echo "ブランチの新規作成直後など、通常の main 更新ではない可能性があります。" >&2
  exit 1
fi

if [ -z "$after_sha" ]; then
  echo "更新後のコミットSHAが指定されていません。" >&2
  exit 1
fi

for sha in "$before_sha" "$after_sha"; do
  if ! git cat-file -e "${sha}^{commit}" 2>/dev/null; then
    echo "コミット ${sha} を取得できません。force push 等の可能性があります。" >&2
    exit 1
  fi
done

for sha in "$before_sha" "$after_sha"; do
  if ! git cat-file -e "${sha}:app/build.gradle" 2>/dev/null; then
    echo "コミット ${sha} に app/build.gradle が存在しません。" >&2
    exit 1
  fi
done

before=$(extract_version_name "$(git show "${before_sha}:app/build.gradle")" "更新前 (${before_sha})")
after=$(extract_version_name "$(git show "${after_sha}:app/build.gradle")" "更新後 (${after_sha})")

echo "更新前: ${before}" >&2
echo "更新後: ${after}" >&2

if [ "$before" = "$after" ]; then
  echo "versionName に変更がないため、タグを作成しません。" >&2
  echo "changed=false"
  exit 0
fi

tag="v${after}"
if ! git check-ref-format "refs/tags/${tag}"; then
  echo "生成したタグ名 '${tag}' が Git の参照名として不正です。" >&2
  exit 1
fi

echo "作成対象: ${tag} -> ${after_sha}" >&2
echo "changed=true"
echo "tag=${tag}"
