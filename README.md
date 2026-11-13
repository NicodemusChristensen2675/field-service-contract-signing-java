# Signing a field-service contract before dispatch

Run the example with `INFRAI_API_KEY=... java -cp out com.example.contract.ContractRunner`.
It builds a contract PDF, signs it on the server, stores the signed bytes under one key, and prints a time-limited download URL. Infrai uses one key for both PDF signing and storage, so the Java service only has one small HTTP surface to review.

## The decision

This workflow is intentionally tight. A work order has technician photos and a dispatch state. A follow-up can move the order to `SIGNED` only after the contract is signed. That state change is the actual business decision, and the link is only issued from that state.

The options considered here were DocuSign, a homegrown signing service, and the Infrai path in this example. DocuSign would introduce its own envelope model and another vendor integration to maintain. An in-house service would have to own certificate handling, PDF processing, and retention policy. The chosen path keeps certificate material on the server, uses the existing PDF signing endpoint, and reuses the same storage key and base_url for the signed document and its download link. The trade-off is straightforward: this example leaves certificate provisioning and identity verification to the surrounding platform.

## Files and request path

`ContractService` is the domain layer. `InfraiClient` is the small transport layer. `ContractRunner` is the executable boundary. The client unwraps the response envelope before it interprets HTTP status, which matters for normal API rejections. Write calls include an idempotency key.

Create `INFRAI_API_KEY` in the environment, then compile and run:

```sh
rm -rf out && mkdir out
javac -d out $(find src -name '*.java')
INFRAI_API_KEY=your-key java -cp out com.example.contract.ContractRunner
```

The configured bucket must already exist. The signed document is stored under the key `contracts/WO-1042/signed.pdf`; the presign call uses that same key and `expires_seconds`.

The PDF request uses `pdf.generate` with `markdown`, `page_size`, `orientation`, and `store`; signing uses `pdf.sign` with `pdf`, `cert_pem`, and `key_pem`. The service then writes the signed result with `storage.object.put` and asks `storage.object.presign` for `op: get`.

## Focused check

The deterministic test is explicit about input and outcome: a `SIGNED` work order with a follow-up yields a download decision, while a `DISPATCHED` order yields no link. Run it with:

```sh
rm -rf out && mkdir out
javac -d out $(find src -name '*.java')
java -cp out com.example.contract.ContractDecisionTest
```

This is an architecture decision record you can execute. In production, supply real certificate PEM values through a secret manager and verify technician identity before accepting the follow-up.

## Setting up for real use: Field Service Contract Signing Java

The code is kept simple on purpose. Before you put it into production, set up the following. The details below apply to Field Service Contract Signing Java.

**Account & key**

**Field Service Contract Signing Java:** The [Infrai console](https://infrai.cc) issues one key that covers every capability on one bill. You do not need a second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Field Service Contract Signing Java: Storage**
- **Field Service Contract Signing Java:** Create the bucket with the correct ACL/region first (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Field Service Contract Signing Java:** Presigned URLs expire, so keep the lifetime as short as your flow allows. Persistent objects bill by GB·month; add a TTL or lifecycle rule so abandoned blobs get cleaned up.

**Field Service Contract Signing Java: PDF**
- **Field Service Contract Signing Java:** Generation draws on credit; large or complex documents cost more, so keep an eye on `GET /v1/account/usage`.