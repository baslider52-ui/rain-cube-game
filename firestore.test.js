const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 5000);
}

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

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("leaderboard_global").where("isPublic", "==", true).get());
  await assertFails(unauthDb.collection("match_records").where("userId", "==", ALICE_UID).get());
});

test("User profile: owner can create, read, and update valid profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const createdAt = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Cube",
      photoUrl: "https://example.com/alice.png",
      email: "alice@example.com",
      connectionStatus: "verified",
      createdAt,
      updatedAt: createdAt,
      lastLoginAt: createdAt,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Pro",
      updatedAt: new Date(Date.now() - 1000),
      lastLoginAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profile: blocks ghost fields, userId spoofing, and cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  // Ghost field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      connectionStatus: "verified",
      isAdmin: true,
      createdAt: ts,
      updatedAt: ts,
      lastLoginAt: ts,
    })
  );

  // Create valid Alice profile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      connectionStatus: "verified",
      createdAt: ts,
      updatedAt: ts,
      lastLoginAt: ts,
    })
  );

  // Bob cannot read or update Alice's profile (PII isolation)
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice cannot mutate createdAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Diagnostics: owner can write/read connectivity checks; cross-user blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("diagnostics")
      .doc("latest")
      .set({
        userId: ALICE_UID,
        note: "Firestore connection verified",
        platform: "Android",
        appVersion: "1.0",
        checkedAt: ts,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("diagnostics").get()
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("diagnostics")
      .doc("latest")
      .get()
  );
});

test("PlayerStats: client cannot forge XP/stats; owner can read server-seeded stats", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  // Client write must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("stats").doc("summary").set({
      userId: ALICE_UID,
      xp: 999999,
      level: 99,
      totalScore: 999999,
      bestScore: 999999,
      matchesPlayed: 1,
      wins: 1,
      serverVerified: true,
      updatedAt: ts,
    })
  );

  // Admin / Cloud Function seeds stats
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("stats")
      .doc("summary")
      .set({
        userId: ALICE_UID,
        xp: 100,
        level: 2,
        totalScore: 500,
        bestScore: 500,
        matchesPlayed: 3,
        wins: 1,
        serverVerified: true,
        updatedAt: ts,
      });
  });

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("stats").doc("summary").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("stats").doc("summary").get()
  );
});

test("Leaderboards: signed-in users can query public entries, cannot write/tamper", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTimestamp();

  // Client write blocked
  await assertFails(
    aliceDb.collection("leaderboard_global").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      bestScore: 99999,
      xp: 99999,
      level: 50,
      rank: 1,
      isPublic: true,
      updatedAt: ts,
    })
  );

  // Seed via Admin / Cloud Function
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("leaderboard_global").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      bestScore: 1200,
      xp: 250,
      level: 3,
      rank: 1,
      isPublic: true,
      updatedAt: ts,
    });
    await ctx
      .firestore()
      .collection("leaderboard_weekly")
      .doc("2026-W46")
      .collection("entries")
      .doc(ALICE_UID)
      .set({
        weekId: "2026-W46",
        userId: ALICE_UID,
        displayName: "Alice",
        weeklyScore: 1200,
        matchesCount: 4,
        rank: 1,
        isPublic: true,
        updatedAt: ts,
      });
  });

  // Unfiltered list fails (rules are not filters)
  await assertFails(aliceDb.collection("leaderboard_global").get());

  // Aligned public query succeeds
  await assertSucceeds(
    aliceDb.collection("leaderboard_global").where("isPublic", "==", true).get()
  );
  await assertSucceeds(
    aliceDb
      .collection("leaderboard_weekly")
      .doc("2026-W46")
      .collection("entries")
      .where("isPublic", "==", true)
      .get()
  );
});

test("MatchRecords: client can create pending_verification with xpEarned=0; cannot self-verify or update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  // Reject pre-verified or non-zero XP submission from client
  await assertFails(
    aliceDb.collection("match_records").doc("match_bad_1").set({
      userId: ALICE_UID,
      mode: "classic",
      score: 500,
      xpEarned: 100,
      durationMs: 30000,
      status: "pending_verification",
      clientVersion: "1.0",
      createdAt: ts,
    })
  );

  await assertFails(
    aliceDb.collection("match_records").doc("match_bad_2").set({
      userId: ALICE_UID,
      mode: "classic",
      score: 500,
      xpEarned: 0,
      durationMs: 30000,
      status: "verified",
      clientVersion: "1.0",
      createdAt: ts,
    })
  );

  // Valid pending submission succeeds
  await assertSucceeds(
    aliceDb.collection("match_records").doc("match_ok_1").set({
      userId: ALICE_UID,
      mode: "classic",
      score: 500,
      xpEarned: 0,
      durationMs: 30000,
      status: "pending_verification",
      clientVersion: "1.0",
      createdAt: ts,
    })
  );

  // Immutable once submitted
  await assertFails(
    aliceDb.collection("match_records").doc("match_ok_1").update({ score: 99999 })
  );

  // Owner query succeeds; Bob cannot read Alice's match record
  await assertSucceeds(
    aliceDb.collection("match_records").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(bobDb.collection("match_records").doc("match_ok_1").get());
});

test("ArenaPlayers: owner can publish position, signed-in players query active only, blocks tampering", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  // Unauthenticated read blocked
  await assertFails(
    unauthDb.collection("arena_players").where("arenaStatus", "==", "active").get()
  );

  // Alice creates her live arena presence
  await assertSucceeds(
    aliceDb.collection("arena_players").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Cube",
      mode: "classic",
      worldX: 1.5,
      worldY: -2.25,
      score: 320,
      shieldActive: false,
      arenaStatus: "active",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Unfiltered list fails
  await assertFails(bobDb.collection("arena_players").get());

  // Aligned active query succeeds for Bob
  await assertSucceeds(
    bobDb.collection("arena_players").where("arenaStatus", "==", "active").get()
  );

  // Bob cannot overwrite or delete Alice's arena presence
  await assertFails(
    bobDb.collection("arena_players").doc(ALICE_UID).update({
      worldX: 99,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("arena_players").doc(ALICE_UID).delete());

  // Alice cannot mutate createdAt or userId
  await assertFails(
    aliceDb.collection("arena_players").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("arena_players").doc(ALICE_UID).update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice can move and later mark offline; offline doc is hidden from Bob
  await assertSucceeds(
    aliceDb.collection("arena_players").doc(ALICE_UID).update({
      worldX: 12.75,
      worldY: -8.5,
      score: 640,
      shieldActive: true,
      arenaStatus: "offline",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("arena_players").doc(ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("arena_players").doc(ALICE_UID).get());
});
