const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const ts = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function createUserWithCode(uid, name, code) {
  const db = testEnv.authenticatedContext(uid).firestore();
  const batch = db.batch();
  batch.set(db.collection("referralCodes").doc(code), {
    ownerUid: uid,
    ownerDisplayName: name,
    code,
    createdAt: ts(),
  });
  batch.set(db.collection("users").doc(uid), {
    userId: uid,
    displayName: name,
    referralCode: code,
    bonusShields: 0,
    starShards: 0,
    referralCount: 0,
    bestScore: 0,
    createdAt: ts(),
    updatedAt: ts(),
  });
  await batch.commit();
}

test("Unauthenticated user cannot read or write users, codes, or redemptions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("referralCodes").doc("NEON1234").get());
  await assertFails(unauthDb.collection("referralRedemptions").get());
});

test("Authenticated user can atomically register profile and unique referral code", async () => {
  await assertSucceeds(createUserWithCode(ALICE_UID, "Alice", "NEONALICE1"));
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("User cannot register profile with pre-inflated shields or shards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("referralCodes").doc("NEONALICE2"), {
    ownerUid: ALICE_UID,
    ownerDisplayName: "Alice",
    code: "NEONALICE2",
    createdAt: ts(),
  });
  batch.set(aliceDb.collection("users").doc(ALICE_UID), {
    userId: ALICE_UID,
    displayName: "Alice",
    referralCode: "NEONALICE2",
    bonusShields: 99,
    starShards: 0,
    referralCount: 0,
    bestScore: 0,
    createdAt: ts(),
    updatedAt: ts(),
  });
  await assertFails(batch.commit());
});

test("Cross-user isolation: Bob cannot read Alice profile or list referralCodes", async () => {
  await createUserWithCode(ALICE_UID, "Alice", "NEONALICE3");
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("referralCodes").get());
  await assertSucceeds(bobDb.collection("referralCodes").doc("NEONALICE3").get());
});

test("User cannot self-refer with their own referral code", async () => {
  await createUserWithCode(ALICE_UID, "Alice", "NEONALICE4");
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("referralRedemptions").doc(ALICE_UID), {
    redeemerUid: ALICE_UID,
    redeemerName: "Alice",
    code: "NEONALICE4",
    referrerUid: ALICE_UID,
    createdAt: ts(),
  });
  batch.update(aliceDb.collection("users").doc(ALICE_UID), {
    referredByCode: "NEONALICE4",
    referredByUid: ALICE_UID,
    bonusShields: 1,
    starShards: 100,
    updatedAt: ts(),
  });
  await assertFails(batch.commit());
});

test("Bob can redeem Alice referral code atomically, cannot redeem a second time, and Alice can claim reward once", async () => {
  await createUserWithCode(ALICE_UID, "Alice", "NEONALICE5");
  await createUserWithCode(BOB_UID, "Bob", "NEONBOB5");
  await createUserWithCode(CHARLIE_UID, "Charlie", "NEONCHAR5");

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const redeemBatch = bobDb.batch();
  redeemBatch.set(bobDb.collection("referralRedemptions").doc(BOB_UID), {
    redeemerUid: BOB_UID,
    redeemerName: "Bob",
    code: "NEONALICE5",
    referrerUid: ALICE_UID,
    createdAt: ts(),
  });
  redeemBatch.update(bobDb.collection("users").doc(BOB_UID), {
    referredByCode: "NEONALICE5",
    referredByUid: ALICE_UID,
    bonusShields: 1,
    starShards: 100,
    updatedAt: ts(),
  });
  await assertSucceeds(redeemBatch.commit());

  // Second redemption attempt by Bob must fail
  const secondBatch = bobDb.batch();
  secondBatch.set(bobDb.collection("referralRedemptions").doc(BOB_UID), {
    redeemerUid: BOB_UID,
    redeemerName: "Bob",
    code: "NEONCHAR5",
    referrerUid: CHARLIE_UID,
    createdAt: ts(),
  });
  secondBatch.update(bobDb.collection("users").doc(BOB_UID), {
    referredByCode: "NEONCHAR5",
    referredByUid: CHARLIE_UID,
    bonusShields: 2,
    starShards: 200,
    updatedAt: ts(),
  });
  await assertFails(secondBatch.commit());

  // Alice lists her redemptions and claims reward atomically
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("referralRedemptions").where("referrerUid", "==", ALICE_UID).get()
  );

  const claimBatch = aliceDb.batch();
  claimBatch.set(
    aliceDb.collection("users").doc(ALICE_UID).collection("claimedReferrals").doc(BOB_UID),
    {
      redeemerUid: BOB_UID,
      redeemerName: "Bob",
      rewardShields: 1,
      rewardShards: 150,
      claimedAt: ts(),
    }
  );
  claimBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    bonusShields: 1,
    starShards: 150,
    referralCount: 1,
    lastClaimedRedeemerUid: BOB_UID,
    updatedAt: ts(),
  });
  await assertSucceeds(claimBatch.commit());

  // Duplicate claim by Alice must fail
  const dupClaimBatch = aliceDb.batch();
  dupClaimBatch.set(
    aliceDb.collection("users").doc(ALICE_UID).collection("claimedReferrals").doc(BOB_UID),
    {
      redeemerUid: BOB_UID,
      redeemerName: "Bob",
      rewardShields: 1,
      rewardShards: 150,
      claimedAt: ts(),
    }
  );
  dupClaimBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    bonusShields: 2,
    starShards: 300,
    referralCount: 2,
    lastClaimedRedeemerUid: BOB_UID,
    updatedAt: ts(),
  });
  await assertFails(dupClaimBatch.commit());
});

test("User cannot arbitrarily inflate bonusShields or add ghost fields", async () => {
  await createUserWithCode(ALICE_UID, "Alice", "NEONALICE6");
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bonusShields: 5,
      updatedAt: ts(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bestScore: 500,
      isAdmin: true,
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bestScore: 500,
      updatedAt: ts(),
    })
  );
});
