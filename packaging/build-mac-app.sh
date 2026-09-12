#!/usr/bin/env bash
# Java 설치 없이 더블클릭으로 실행되는 Mac 앱(.app)을 만든다.
#   사용법: packaging/build-mac-app.sh ["앱 이름"]
# 앱 이름은 Dock·Finder에 보이는 이름이자 화면 제목의 상호(app.store-name)로 쓰인다. 기본값: Store Ledger
# 만드는 Mac에 JDK 21이 있어야 하고, 결과물은 같은 종류의 칩(Apple Silicon/Intel)을 쓰는 Mac에서 실행된다.
set -euo pipefail
export LC_ALL=en_US.UTF-8   # 앱 이름을 바이트가 아닌 글자 단위로 자르도록 (한글 이름)
cd "$(dirname "$0")/.."

NAME="${1:-Store Ledger}"
VERSION=$(sed -n "s/^version = '\(.*\)'/\1/p" build.gradle)
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
OUT=build/mac-app

echo "▶ jar 빌드"
./gradlew -q clean bootJar
JAR=$(ls build/libs/storeledger-*.jar | grep -v -- '-plain.jar')

rm -rf "$OUT"
mkdir -p "$OUT/input" "$OUT/icon.iconset"
cp "$JAR" "$OUT/input/storeledger.jar"

echo "▶ 아이콘 변환 (png → icns)"
for px in 16 32 128 256 512; do
  sips -z $px $px packaging/icon.png --out "$OUT/icon.iconset/icon_${px}x${px}.png" >/dev/null
  sips -z $((px * 2)) $((px * 2)) packaging/icon.png --out "$OUT/icon.iconset/icon_${px}x${px}@2x.png" >/dev/null
done
iconutil -c icns "$OUT/icon.iconset" -o "$OUT/icon.icns"

# --add-modules 를 주지 않으면 컴파일러·javadoc까지 JDK 전체가 들어가 앱이 220MB가 된다.
echo "▶ jpackage: $NAME.app (버전 $VERSION)"
"$JAVA_HOME/bin/jpackage" \
  --type app-image \
  --dest "$OUT" \
  --name "$NAME" \
  --app-version "$VERSION" \
  --input "$OUT/input" \
  --main-jar storeledger.jar \
  --icon "$OUT/icon.icns" \
  --add-modules java.se,jdk.unsupported,jdk.charsets,jdk.localedata,jdk.zipfs,jdk.crypto.ec \
  --jlink-options "--strip-debug --no-man-pages --no-header-files --strip-native-commands --compress zip-6" \
  --java-options "-Dapp.desktop=true" \
  --java-options "-Dapp.store-name=\"$NAME\"" \
  --java-options "-Xmx512m" \
  --mac-package-identifier com.storeledger.app \
  --mac-package-name "${NAME:0:16}" \
  --vendor "Store Ledger" \
  --description "매출·마진 관리"

APP="$OUT/$NAME.app"
codesign --force --sign - "$APP"   # jpackage가 실행 파일·런타임은 이미 ad-hoc 서명함. 번들 전체를 다시 봉인만 한다 (--deep 은 codesign 이 죽음)
ditto -c -k --keepParent "$APP" "$OUT/$NAME.zip"       # 다른 Mac으로 옮길 때 쓰는 압축본

echo
echo "완성: $APP"
echo "전달용: $OUT/$NAME.zip"
echo "응용 프로그램 폴더에 넣고 더블클릭하면 브라우저가 열립니다. 데이터는 ~/StoreLedger 에 저장됩니다."
echo "다른 Mac으로는 zip을 옮기세요. '손상됨' 경고가 뜨면 README의 xattr 명령을 받은 Mac에서 한 번 실행합니다."
