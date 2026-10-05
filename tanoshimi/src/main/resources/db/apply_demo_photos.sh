#!/usr/bin/env bash
# 시연용 사진 적용 - static/assets/places/*.jpg 를 업로드 폴더에 demo_*.jpg 로 복사한 뒤
# demo_photos_seed.sql 로 게시물/액티비티/투어 썸네일을 그 사진으로 바꾼다. 여러 번 실행해도 안전.
#
# 사용법(Git Bash, tanoshimi/tanoshimi 기준):
#   bash src/main/resources/db/apply_demo_photos.sh            # mysql 접속 계정 root
#   DB_USER=scit bash src/main/resources/db/apply_demo_photos.sh
# 업로드 폴더는 application.yml 의 upload-dir 기본값(~/tanoshimi-uploads)과 같다.
# 서버를 UPLOAD_DIR 환경변수로 띄웠다면 같은 값을 넘겨줄 것.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
places="$here/../static/assets/places"
upload_dir="${UPLOAD_DIR:-$HOME/tanoshimi-uploads}"
db_user="${DB_USER:-root}"
mysql_bin="${MYSQL:-mysql}"

mkdir -p "$upload_dir"
count=0
for f in "$places"/*.jpg; do
    cp -f "$f" "$upload_dir/demo_$(basename "$f")"
    count=$((count + 1))
done
echo "사진 ${count}장 복사 -> $upload_dir"

# MYSQL_PWD 가 있으면 그걸 쓰고, 없으면 비밀번호를 물어본다.
pw_flag=(-p)
[ -n "${MYSQL_PWD:-}" ] && pw_flag=()
"$mysql_bin" -u "$db_user" "${pw_flag[@]}" --default-character-set=utf8mb4 < "$here/demo_photos_seed.sql"
