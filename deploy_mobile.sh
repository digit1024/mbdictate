#!/usr/bin/env zsh

# mbdictate - Production/Release Deployment Script
# Deploys the Flutter dictation app to a connected device in release mode

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Icons
CHECK="✓"
CROSS="✗"
ARROW="→"
PHONE="📱"
BUILD="🔨"
ROCKET="🚀"

# Script directory (works in both zsh and bash)
if [ -n "$ZSH_VERSION" ]; then
    SCRIPT_DIR="$(cd "$(dirname "${(%):-%x}")" && pwd)"
elif [ -n "$BASH_VERSION" ]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
else
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
fi
APP_DIR="${SCRIPT_DIR}"

# Parse command line arguments
PLATFORM_ARG=""
DEVICE_ID=""
while [ $# -gt 0 ]; do
    case "$1" in
        --android|-a)
            PLATFORM_ARG="android"
            ;;
        --ios|-i)
            PLATFORM_ARG="ios"
            ;;
        --device|-d)
            shift
            DEVICE_ID="$1"
            ;;
        --device=*)
            DEVICE_ID="${1#--device=}"
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Deploy mbdictate to a connected device in release mode"
            echo ""
            echo "Options:"
            echo "  --android, -a      Force Android deployment"
            echo "  --ios, -i          Force iOS deployment"
            echo "  --device, -d ID    Target a specific device id"
            echo "  --help, -h         Show this help message"
            echo ""
            echo "If no platform is specified, the script will auto-detect from connected devices."
            exit 0
            ;;
        *)
            echo "${RED}${CROSS} Unknown option: $1${NC}"
            echo "${YELLOW}Run $0 --help for usage${NC}"
            exit 1
            ;;
    esac
    shift
done

# Optional device selector for flutter commands
DEVICE_FLAG=()
if [ -n "$DEVICE_ID" ]; then
    DEVICE_FLAG=(-d "$DEVICE_ID")
fi

echo "${BLUE}${ROCKET} mbdictate - Production Deployment${NC}\n"

# Check if Flutter is installed
if ! command -v flutter &> /dev/null; then
    echo "${RED}${CROSS} Flutter is not installed or not in PATH${NC}"
    exit 1
fi

echo "${GREEN}${CHECK} Flutter found${NC}"

# Navigate to the app directory
cd "${APP_DIR}" || exit 1

# Check for connected devices
echo "\n${BLUE}${PHONE} Checking for connected devices...${NC}"
set +e  # Temporarily disable exit on error
DEVICE_OUTPUT=$(flutter devices 2>/dev/null)
DEVICE_COUNT=$(flutter devices --machine 2>/dev/null | grep -c '"id"')
set -e  # Re-enable exit on error

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "${RED}${CROSS} No devices or emulators found${NC}"
    echo "${YELLOW}Please connect a device or start an emulator${NC}"
    exit 1
fi

# Display connected devices
echo "${GREEN}${CHECK} Connected devices:${NC}"
echo "$DEVICE_OUTPUT"

# Determine platform
PLATFORM=""
if [ -n "$PLATFORM_ARG" ]; then
    PLATFORM="$PLATFORM_ARG"
    echo "${BLUE}${ARROW} Platform specified: ${PLATFORM}${NC}"
else
    # Auto-detect platform from connected devices
    set +e  # Temporarily disable exit on error
    ANDROID_COUNT=$(echo "$DEVICE_OUTPUT" | grep -ic 'android\|chrome' || echo "0")
    IOS_COUNT=$(echo "$DEVICE_OUTPUT" | grep -ic 'ios\|iphone\|ipad' || echo "0")
    set -e  # Re-enable exit on error

    if [ "$ANDROID_COUNT" -gt 0 ] && [ "$IOS_COUNT" -eq 0 ]; then
        PLATFORM="android"
    elif [ "$IOS_COUNT" -gt 0 ] && [ "$ANDROID_COUNT" -eq 0 ]; then
        PLATFORM="ios"
    elif [ "$ANDROID_COUNT" -gt 0 ] && [ "$IOS_COUNT" -gt 0 ]; then
        echo "${YELLOW}⚠ Multiple platforms detected. Defaulting to Android.${NC}"
        echo "${YELLOW}   Use --android or --ios to specify explicitly${NC}"
        PLATFORM="android"
    else
        echo "${RED}${CROSS} Could not detect platform from connected devices${NC}"
        echo "${YELLOW}   Use --android or --ios to specify platform${NC}"
        exit 1
    fi
fi

# Verify device is available for selected platform
set +e  # Temporarily disable exit on error
if [ "$PLATFORM" = "android" ]; then
    if ! echo "$DEVICE_OUTPUT" | grep -qi 'android\|chrome'; then
        echo "${RED}${CROSS} No Android device found${NC}"
        exit 1
    fi
elif [ "$PLATFORM" = "ios" ]; then
    if ! echo "$DEVICE_OUTPUT" | grep -qi 'ios\|iphone\|ipad'; then
        echo "${RED}${CROSS} No iOS device found${NC}"
        exit 1
    fi
fi
set -e  # Re-enable exit on error

echo "\n${BLUE}${ARROW} Deploying to: ${PLATFORM}${NC}\n"

# Clean previous builds
echo "${BLUE}${BUILD} Cleaning previous builds...${NC}"
flutter clean

# Get dependencies
echo "${BLUE}${BUILD} Getting dependencies...${NC}"
flutter pub get

# Build and deploy based on platform
if [ "$PLATFORM" = "android" ]; then
    echo "\n${BLUE}${BUILD} Building Android release APK...${NC}"
    flutter build apk --release

    APK_PATH="build/app/outputs/flutter-apk/app-release.apk"
    if [ ! -f "$APK_PATH" ]; then
        echo "${RED}${CROSS} APK not found at expected path${NC}"
        exit 1
    fi

    echo "\n${BLUE}${ROCKET} Installing to Android device...${NC}"
    if command -v adb &> /dev/null; then
        ADB_TARGET=()
        if [ -n "$DEVICE_ID" ]; then
            ADB_TARGET=(-s "$DEVICE_ID")
        fi
        A11Y_SERVICES="$(adb "${ADB_TARGET[@]}" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')"
        # Reinstall in place so app data and granted permissions survive.
        adb "${ADB_TARGET[@]}" install -r "$APK_PATH"
        # Updates can leave the accessibility service unbound; re-assert it.
        if [[ "$A11Y_SERVICES" == *com.example.mbdictate* ]]; then
            echo "${BLUE}${ARROW} Re-binding accessibility service after update...${NC}"
            adb "${ADB_TARGET[@]}" shell settings put secure enabled_accessibility_services null
            sleep 1
            adb "${ADB_TARGET[@]}" shell settings put secure enabled_accessibility_services "$A11Y_SERVICES"
        fi
    else
        flutter install --release "${DEVICE_FLAG[@]}"
    fi

    echo "\n${GREEN}${CHECK} APK built successfully: ${APK_PATH}${NC}"
    echo "${GREEN}${CHECK} App installed to device${NC}"

elif [ "$PLATFORM" = "ios" ]; then
    echo "\n${BLUE}${BUILD} Building iOS release...${NC}"
    flutter build ios --release --no-codesign

    echo "\n${BLUE}${ROCKET} Installing to iOS device...${NC}"
    flutter install --release "${DEVICE_FLAG[@]}"

    echo "\n${GREEN}${CHECK} iOS app built and installed${NC}"
    echo "${YELLOW}⚠ Note: For App Store distribution, you'll need to archive and sign in Xcode${NC}"
fi

echo "\n${GREEN}${CHECK} On first launch, grant microphone, display-over-other-apps,"
echo "accessibility and notification permissions, then add an STT provider API key"
echo "in Settings before starting the dictation service.${NC}"

echo "\n${GREEN}${CHECK}${CHECK}${CHECK} Deployment complete!${NC}\n"