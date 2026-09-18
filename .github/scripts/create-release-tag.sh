#!/usr/bin/env bash
#
# 役割: 指定されたコミットへ注釈付きタグを作成し、リモートへpushする。
# 引数: $1 タグ名 (例 v1.1.0) / $2 対象コミットSHA
# 副作用: origin へのタグ作成。既存タグの上書き・削除・付け替えは行わない。
# 備考: versionNameの読み取りやバージョン比較は行わない。
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <tag> <commit-sha>" >&2
  exit 1
fi

tag="$1"
commit_sha="$2"

if [ -z "$tag" ] || [ -z "$commit_sha" ]; then
  echo "タグ名とコミットSHAの両方を指定してください。" >&2
  exit 1
fi

if ! git check-ref-format "refs/tags/${tag}"; then
  echo "タグ名 '${tag}' が Git の参照名として不正です。" >&2
  exit 1
fi

if ! git cat-file -e "${commit_sha}^{commit}" 2>/dev/null; then
  echo "コミット ${commit_sha} を取得できません。" >&2
  exit 1
fi

existing=$(git ls-remote --tags origin "refs/tags/${tag}" | cut -f1)

if [ -n "$existing" ]; then
  # 注釈付きタグは、指し先コミットのSHAで比較する。
  target=$(git ls-remote --tags origin "refs/tags/${tag}^{}" | cut -f1)
  [ -n "$target" ] || target="$existing"

  if [ "$target" = "$commit_sha" ]; then
    echo "タグ ${tag} は既に同じコミット (${commit_sha}) に存在します。再実行のため何もしません。" >&2
    exit 0
  fi

  echo "タグ ${tag} は別のコミット (${target}) を指しています。" >&2
  echo "既存タグの付け替えは行いません。versionName の更新内容を確認してください。" >&2
  exit 1
fi

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git tag -a "$tag" "$commit_sha" -m "Release ${tag}"

# 同時実行で拒否された場合も force push は行わない。
git push origin "refs/tags/${tag}"
echo "タグ ${tag} を ${commit_sha} に作成しました。" >&2
