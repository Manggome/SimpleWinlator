<p align="center">
	<img src="logo.png" width="376" height="128" alt="Winlator Logo" />
</p>

# SimpleWinlator

[Winlator](https://github.com/brunodev85/winlator) 11.2 를 고쳐서 **홈 화면에 게임만 보이고, 누르면 바로 실행되게** 만든 포크입니다.
컨테이너를 만들고, 윈도우 바탕화면에서 파일을 찾아 들어가는 과정을 없앴습니다.

## 쓰는 법

1. [Releases](https://github.com/Manggome/SimpleWinlator/releases) 에서 `SimpleWinlator-...-arm64.apk` 를 받아 설치합니다
2. 처음 켜면 저장공간 권한을 허용하고, 시스템 파일 설치가 끝날 때까지 기다립니다 (한 번만)
3. 오른쪽 위 **+** 를 눌러 게임 폴더에서 `.exe` 파일을 고릅니다
   - 처음 한 번은 윈도우 환경(기본 컨테이너)을 자동으로 만듭니다
4. 게임 아이콘이 홈 화면에 생깁니다. 누르면 바로 실행되고, 게임을 끄면 다시 홈으로 돌아옵니다

게임별 설정(해상도, 그래픽 드라이버, DXVK, Box64 프리셋, 조작 버튼 등)은 게임 아이콘의 **⋮ → 설정** 에서 바꿀 수 있습니다.
컨테이너 편집, 조작 버튼 편집, 앱 설정 같은 고급 기능은 왼쪽 메뉴에 그대로 있습니다.

> ⚠ 원본 Winlator 와 **같은 패키지명(`com.winlator`)** 을 씁니다. Winlator 내부 파일에 이 경로가 박혀 있어서
> 바꿀 수 없습니다. 그래서 원본 Winlator 와 동시에 설치할 수 없고, 원본이 깔려 있으면 지우고 설치해야 합니다.

## 원본과 달라진 점

- 홈 화면 = 게임 목록 (원본의 "바로가기" 화면을 게임 라이브러리로 사용)
- **게임 추가** 버튼: 내장 저장공간을 훑어 `.exe` / `.bat` / `.lnk` 를 고르면 아이콘을 뽑아 바로가기를 만듭니다
- 컨테이너가 없으면 기본 컨테이너를 자동으로 만들고, 내장 저장공간 전체를 `F:` 드라이브로 연결합니다
- 뒤로 가기는 항상 게임 목록으로 돌아옵니다
- 한국어 문자열 추가, 앱 이름 변경
- GitHub Actions 로 APK 빌드 (`v*` 태그를 올리면 Release 에 APK 가 붙습니다)

추가된 코드는 대부분 `app/src/main/java/com/winlator/simple/` 에 모여 있어서 원본 업데이트를 합치기 쉽게 해 두었습니다.

## 빌드

`main` 에 푸시하면 Actions 가 빌드해서 아티팩트로 올립니다. 직접 빌드하려면 JDK 17, Android SDK 34,
NDK `24.0.8215888`, CMake `3.22.1` 을 준비하고:

```
./gradlew assembleDebug
```

서명 키는 저장소 시크릿(`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`)으로 넣습니다.
시크릿이 없으면 디버그 키로 서명됩니다.

## 원본 업데이트 합치기

```
git remote add upstream https://github.com/brunodev85/winlator-app
git fetch upstream
git merge upstream/main
```

## 라이선스 · 크레딧

원본 Winlator 와 같은 LGPL-2.1 라이선스를 따릅니다 ([`LICENSE`](LICENSE)). Winlator 는 [brunodev85](https://github.com/brunodev85) 의 작품입니다.

- GLIBC Patches by [Termux Pacman](https://github.com/termux-pacman/glibc-packages)
- Wine ([winehq.org](https://www.winehq.org/))
- Box86/Box64 by [ptitseb](https://github.com/ptitSeb)
- Mesa (Turnip/Zink/VirGL) ([mesa3d.org](https://www.mesa3d.org))
- DXVK ([github.com/doitsujin/dxvk](https://github.com/doitsujin/dxvk))
- VKD3D ([gitlab.winehq.org/wine/vkd3d](https://gitlab.winehq.org/wine/vkd3d))
- CNC DDraw ([github.com/FunkyFr3sh/cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw))
