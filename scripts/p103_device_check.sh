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
send_fix() {
    local response
    response=$(timeout 5 adb emu geo fix "$1" "$2" 0 8 0 | tr -d '\r') || return
    if ! grep -Fqx 'OK' <<< "$response"; then
        printf 'Emulator rejected GPS input: %s\n' "$response" >&2
        return 1
    fi
}
case "${1:-}" in
    nearby)
        run_test -e class "$places.P7IntegrationTest#nearbyPermissionCityDisabledProviderAndNetworkRecovery"
        ;;
    gps)
        adb shell cmd location set-location-enabled true
        adb shell pm grant ru.krapiva.app.debug android.permission.ACCESS_COARSE_LOCATION
        adb shell pm grant ru.krapiva.app.debug android.permission.ACCESS_FINE_LOCATION
        gps_status=0
        while read -r city lat lon; do
            echo "Checking $city"
            send_fix "$lon" "$lat"
            # Keep supplying fixes until instrumentation exits, including a cold start.
            (while send_fix "$lon" "$lat"; do sleep 2; done) &
            sender=$!
            trap 'kill "$sender" 2>/dev/null || true' EXIT
            if ! run_test -e class "$places.DeviceFixTest" -e latitude "$lat" -e longitude "$lon" </dev/null; then
                gps_status=1
            fi
            kill "$sender" 2>/dev/null || true
            wait "$sender" 2>/dev/null || true
            trap - EXIT
        done <<'CITIES'
Moscow 55.7558 37.6173
St-Petersburg 59.9343 30.3351
Suzdal 56.4197 40.4495
CITIES
        exit "$gps_status"
        ;;
    *) exit 2 ;;
esac
