#!/usr/bin/env bash
# Fires 10 booking requests for the SAME slot at the same instant and prints each HTTP status.
# Expected output: exactly one "201" and nine "409".
# Usage: start the app (mvn spring-boot:run), then run  ./scripts/concurrent-booking-demo.sh
BASE=${BASE:-http://localhost:8080}

SLOT_ID=$(curl -s -u admin:admin123 -X POST "$BASE/slots" \
  -H 'Content-Type: application/json' \
  -d '{"startTime":"2030-06-01T10:00:00","endTime":"2030-06-01T11:00:00"}' \
  | sed -E 's/.*"id":([0-9]+).*/\1/')
echo "Created slot id=$SLOT_ID"

for i in $(seq 1 10); do
  if [ $((i % 2)) -eq 0 ]; then USER=user1; else USER=user2; fi
  ( code=$(curl -s -o /dev/null -w '%{http_code}' -u $USER:user123 -X POST "$BASE/bookings" \
      -H 'Content-Type: application/json' -d "{\"slotId\":$SLOT_ID}")
    echo "request $i ($USER) -> HTTP $code" ) &
done
wait

echo "Final slot state:"
curl -s -u user1:user123 "$BASE/slots"
echo
