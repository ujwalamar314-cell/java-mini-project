# Multi-Threaded ATM System — Java Mini Project

Several ATMs serve customers **at the same time** and **block invalid operations**.
Pure Java SE (8 or newer), **zero dependencies** — no Maven, no Gradle, no JUnit,
no database. Just `javac` and `java`.

Every class lives in the package **`atm`**.

---

## Requirements

Any JDK 8 or newer. Check with:

```bash
javac -version
java -version
```

## Build, run, test

**Linux / macOS**

```bash
./build.sh     # compiles src/ -> out/
./run.sh       # runs the demo:      java -cp out atm.ATMSystem
./test.sh      # runs the 90 checks: java -ea -cp out atm.SelfTest
```

**Windows**

```bat
build.bat
run.bat
test.bat
```

**By hand** (what the scripts do internally)

```bash
mkdir -p out
javac -encoding UTF-8 -d out src/atm/*.java
java        -cp out atm.ATMSystem     # the demo
java -ea    -cp out atm.SelfTest      # the tests
```

---

## Requirement → implementation map

| Requirement | Where it is implemented |
|---|---|
| **Class `Card`** (card no., PIN, account) **with constructor overloading** | `Card.java` — fields `cardNumber`, `pin`, `account`; **four** constructors with different parameter lists: `(String,String,Account)`, `(String,String)`, `(long,int,Account)`, `()`. Constructors 2–4 delegate to the canonical one via `this(...)`. |
| **Inheritance: abstract `ATMOperation`** | `ATMOperation.java` — `public abstract class`, declares `public abstract String execute()` plus the shared `authenticate()` step. Cannot be instantiated. |
| **`Withdrawal` overrides `execute()`** | `Withdrawal.java` — `extends ATMOperation`; authenticates, debits the account, dispenses from the vault, and **rolls the debit back** if the vault is empty. |
| **`BalanceEnquiry` overrides `execute()`** | `BalanceEnquiry.java` — `extends ATMOperation`; overrides `execute()` with a narrower `throws` clause (only `InvalidPinException`) because it moves no cash. |
| **Interface `Authenticator` (`verifyPin`) in package `atm`** | `Authenticator.java` (`interface`, `package atm;`) implemented by `PinAuthenticator.java`. Operations depend only on the interface, so the PIN strategy is swappable. |
| **Custom `InvalidPinException`** | `InvalidPinException.java` — checked exception, thrown by `ATMOperation.authenticate()`; carries the masked card number and holder. |
| **Custom `DailyLimitExceededException`** | `DailyLimitExceededException.java` — checked exception, thrown by `Account.withdraw()`; carries attempted amount, limit, already-withdrawn and remaining limit. |
| **Each ATM is a thread** | `ATM.java` — `public class ATM extends Thread`; `run()` drains that ATM's customer queue and reports each result. |
| **Shared synchronized cash vault** | `CashVault.java` — **one** instance is passed to every `ATM`; all mutators are `synchronized`, so "check cash then take cash" is atomic. `Account`, `PinAuthenticator` and `TransactionLog` are synchronized for the same reason. |
| **Use `join()`** | `ATMSystem.main()` — starts all three ATMs, then `join()`s each one, so the summary is printed only after every thread has terminated. |
| **Block invalid operations** | Four checked exception types, each caught separately in `ATM.run()`: wrong PIN, daily limit breached, insufficient balance, vault out of cash. |

Two extra custom exceptions (`InsufficientBalanceException`,
`InsufficientCashException`) were added so every refusal has a precise type
rather than one vague error.

---

## Project structure

```
java-mini-project/
├── build.sh  run.sh  test.sh          # Linux / macOS
├── build.bat run.bat test.bat         # Windows
├── src/
│   └── atm/                           # everything is in package atm
│       ├── ATMSystem.java             # main() — creates 3 ATM threads, start() + join()
│       ├── ATM.java                   # extends Thread; one per machine
│       ├── ATMOperation.java          # abstract base, abstract execute()
│       ├── Withdrawal.java            # extends ATMOperation, overrides execute()
│       ├── BalanceEnquiry.java        # extends ATMOperation, overrides execute()
│       ├── Card.java                  # card no. + PIN + account, 4 overloaded constructors
│       ├── Account.java               # balance, daily limit, synchronized withdraw/refund
│       ├── CashVault.java             # the ONE shared, synchronized cash vault
│       ├── Authenticator.java         # interface — verifyPin(Card, String)
│       ├── PinAuthenticator.java      # implements Authenticator
│       ├── TransactionLog.java        # thread-safe log + approved/blocked counters
│       ├── InvalidPinException.java           # custom checked exception
│       ├── DailyLimitExceededException.java   # custom checked exception
│       ├── InsufficientBalanceException.java  # custom checked exception
│       ├── InsufficientCashException.java     # custom checked exception
│       └── SelfTest.java              # 90 dependency-free checks
└── out/                               # generated .class files (git-ignored)
```

---

## How the concurrency works

1. `ATMSystem.main()` creates **one** `CashVault` (Rs.12,000), **one**
   `PinAuthenticator`, **one** `TransactionLog` and three `ATM` objects.
2. Each ATM is queued with a mix of valid and invalid customer requests
   (11 transactions in total).
3. `atm.start()` launches all three; they run **simultaneously** and compete for
   the same vault and the same accounts.
4. `atm.join()` makes `main` wait for each thread. Only then is the summary
   printed — which is why the totals in it can be trusted.

Because `CashVault.dispense()` and `Account.withdraw()` are `synchronized`, the
check-then-act sequence is atomic: two ATMs can never both spend the same note,
and the vault can never go negative.

**The ordering of the log lines is not fixed.** That is the point — the ATMs are
genuinely concurrent, so which customer finds the vault empty varies from run to
run. What never varies is the invariant printed at the end:
`dispensed + remaining == opening cash`.

A run can even show the rollback path paying off: if a Rs.4,000 withdrawal is
refused because the vault ran dry, the debit is undone, so the customer's daily
limit is still free and the *next* Rs.3,000 request on the same card succeeds.

---

## Sample output (real, captured from `./run.sh`)

```text
==============================================================
        MULTI-THREADED ATM SYSTEM  (package atm)
==============================================================
Shared cash vault   : Rs.12,000.00
Shared authenticator: atm.PinAuthenticator
Every mutating method of CashVault, Account,
PinAuthenticator and TransactionLog is synchronized.
==============================================================

--- 3 ATM threads starting; all share one synchronized vault ---
[ATM-1] NOTE      thread started, 3 customer(s) waiting, vault holds Rs.12000.00
[ATM-1] APPROVED  Withdrawal of Rs.5000.00 to Asha Rao (card XXXX-XXXX-XXXX-1234) | balance now Rs.20000.00 | withdrawn today Rs.5000.00 of Rs.10000.00 limit
[ATM-1] BLOCKED   DailyLimitExceededException -> Daily limit exceeded for Asha Rao - tried Rs.6000.00, limit Rs.10000.00, already withdrawn Rs.5000.00, only Rs.5000.00 left today
[ATM-1] APPROVED  BalanceEnquiry for Asha Rao (card XXXX-XXXX-XXXX-1234) | available Rs.20000.00 | daily limit left Rs.5000.00
[ATM-1] NOTE      thread finished: 2 approved, 1 blocked
[ATM-3] NOTE      thread started, 4 customer(s) waiting, vault holds Rs.7000.00
[ATM-2] NOTE      thread started, 4 customer(s) waiting, vault holds Rs.7000.00
[ATM-3] BLOCKED   InsufficientCashException -> ATM-3 vault out of cash - customer asked for Rs.9000.00, only Rs.7000.00 left in the machine (debit was rolled back)
[ATM-3] BLOCKED   InvalidPinException -> Invalid PIN for card XXXX-XXXX-XXXX-0000 (Guest) - transaction blocked, account untouched
[ATM-3] BLOCKED   InsufficientBalanceException -> Insufficient balance for Card holder ****2222 - requested Rs.500.00, available Rs.0.00
[ATM-3] APPROVED  BalanceEnquiry for Kiran Kumar (card XXXX-XXXX-XXXX-7777) | available Rs.50000.00 | daily limit left Rs.40000.00
[ATM-3] NOTE      thread finished: 1 approved, 3 blocked
[ATM-2] APPROVED  Withdrawal of Rs.4000.00 to Vikram Shetty (card XXXX-XXXX-XXXX-4444) | balance now Rs.4000.00 | withdrawn today Rs.4000.00 of Rs.5000.00 limit
[ATM-2] BLOCKED   DailyLimitExceededException -> Daily limit exceeded for Vikram Shetty - tried Rs.3000.00, limit Rs.5000.00, already withdrawn Rs.4000.00, only Rs.1000.00 left today
[ATM-2] BLOCKED   InsufficientBalanceException -> Insufficient balance for Meera Nair - requested Rs.5000.00, available Rs.3000.00
[ATM-2] APPROVED  BalanceEnquiry for Meera Nair (card XXXX-XXXX-XXXX-1098) | available Rs.3000.00 | daily limit left Rs.10000.00
[ATM-2] NOTE      thread finished: 2 approved, 2 blocked
--- all ATM threads have finished (join returned) ---

========================= SUMMARY ==========================
Per-ATM thread results:
  ATM-1   thread=ATM-1   state=TERMINATED   approved=2 blocked=1
  ATM-2   thread=ATM-2   state=TERMINATED   approved=2 blocked=2
  ATM-3   thread=ATM-3   state=TERMINATED   approved=1 blocked=3

Shared cash vault after all threads finished:
  opening cash    : Rs.12,000.00
  cash dispensed  : Rs.9,000.00  (2 dispense call(s))
  cash remaining  : Rs.3,000.00
  vault invariant : dispensed + remaining == opening -> HOLDS

Accounts:
  Asha Rao                   balance Rs.   20,000.00   withdrawn today Rs.  5,000.00   limit Rs. 10,000.00
  Vikram Shetty              balance Rs.    4,000.00   withdrawn today Rs.  4,000.00   limit Rs.   5,000.00
  Meera Nair                 balance Rs.    3,000.00   withdrawn today Rs.      0.00   limit Rs.  10,000.00
  Kiran Kumar                balance Rs.   50,000.00   withdrawn today Rs.      0.00   limit Rs.  40,000.00
  Card holder ****2222       balance Rs.        0.00   withdrawn today Rs.      0.00   limit Rs.  10,000.00
  Guest                      balance Rs.        0.00   withdrawn today Rs.      0.00   limit Rs.  10,000.00

PIN verification attempts : 11 (10 ok, 1 rejected)
Transactions approved     : 5
Transactions blocked      : 6
Total transactions        : 11  (per-ATM counters agree: yes)
============================================================
```

Note the interleaving: `ATM-1` and `ATM-3` write to the log in turns, and
`ATM-3` sees only Rs.7,000 left in the vault because `ATM-1` had already taken
Rs.5,000 from it.

---

## Tests — `atm.SelfTest` (90 checks, no JUnit)

```bash
./test.sh
```

Covers:

- **Constructor overloading** — all four `Card` constructors, numeric padding
  (`7L` → `0000000000000007`, `25` → `0025`), masking, and argument validation.
- **Authentication** — correct/wrong/null PIN, null card, attempt counters.
- **Blocking** — each custom exception is raised for the right reason, and the
  account and vault are left untouched when a transaction is refused.
- **Daily limit** — partial use, exact-remaining withdrawal, `resetDailyCounter()`.
- **Rollback** — a withdrawal debited then refused by an empty vault is fully
  reversed (balance *and* daily counter).
- **Inheritance / polymorphism** — reflective proof that `ATMOperation` and its
  `execute()` are abstract, that `Authenticator` is an interface, and that one
  `ATMOperation[]` array dispatches to both subclasses.
- **Threading** — `ATM` really is a `Thread`, `join()` returns only after it
  terminates, and the shared log counters agree with the per-ATM counters.
- **Concurrency stress** — 12 threads × 100 withdrawals against one vault and
  one account:
  - with cash available: exactly Rs.12,000.00 dispensed, exact balance, exact
    daily counter, 1,200 dispense calls, **no lost updates**;
  - with only Rs.500.00 in the vault: exactly 50 withdrawals served, 1,150
    refused, vault ends at Rs.0.00 and **never goes negative**, and every
    rolled-back debit leaves the balance correct.

Remove the `synchronized` keyword from `CashVault` or `Account` and the last two
groups start failing — that is the demonstration that the locks are doing work.

**Verified:** compiles clean at `-source 8 -target 8` with no warnings, and
`90 passed, 0 failed` across 12 consecutive runs.
