#!/usr/bin/env bash
# Use a disposable AVD with the synthetic P7 fixture installed.
set -eu -o pipefail
runner=ru.krapiva.app.debug.test/androidx.test.runner.AndroidJUnitRunner
places=ru.krapiva.app.places
run_test() {
    # am instrument may exit 0 even if the JUnit test failed.
    local result
    result=$(timeout 180 adb shell am instrument -w "$@" "$runner" | tr -d '\r')
    echo "$result"
    grep -Fqx 'OK (1 test)' <<< "$result"
}
case "${1:-}" in
    nearby)
        run_test -e class "$places.P7IntegrationTest#nearbyPermissionCityDisabledProviderAndNetworkRecovery"
        ;;
    gps)
        adb shell cmd location set-location-enabled true
        adb shell pm grant ru.krapiva.app.debug android.permission.ACCESS_COARSE_LOCATION
        adb shell pm grant ru.krapiva.app.debug android.permission.ACCESS_FINE_LOCATION
        while read -r city lat lon; do
            echo "Checking $city"
            # Send fresh fixes while the foreground Activity starts and subscribes.
            (for fix in {1..12}; do adb emu geo fix "$lon" "$lat" >/dev/null; sleep 3; done) &
            sender=$!
            trap 'kill "$sender" 2>/dev/null || true' EXIT
            run_test -e class "$places.DeviceFixTest" -e latitude "$lat" -e longitude "$lon" </dev/null
            kill "$sender" 2>/dev/null || true
            wait "$sender" 2>/dev/null || true
            trap - EXIT
        done <<'CITIES'
Moscow 55.7558 37.6173
St-Petersburg 59.9343 30.3351
Suzdal 56.4197 40.4495
CITIES
        ;;
    *) exit 2 ;;
esac
