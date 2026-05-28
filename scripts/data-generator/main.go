package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"math/rand"
	"os"
	"os/signal"
	"sync"
	"sync/atomic"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var (
	dbURL     = flag.String("db", "postgres://payment_user:payment_pass@localhost:5432/payment_db?sslmode=disable", "PostgreSQL connection URL")
	users     = flag.Int("users", 10000, "Number of users to generate")
	merchants = flag.Int("merchants", 1000, "Number of merchants to generate")
	payments  = flag.Int("payments", 1000000, "Number of payments to generate")
	batch     = flag.Int("batch", 10000, "Batch size for COPY inserts")
	workers   = flag.Int("workers", 8, "Number of concurrent workers for payment generation")
	clean     = flag.Bool("clean", false, "Truncate all tables before inserting")
)

var (
	statuses = []string{"PENDING", "SUCCESS", "FAILED", "REFUNDED", "CANCELLED"}
	types    = []string{"DEBIT", "CREDIT", "TRANSFER", "REFUND"}

	userIDs     []string
	merchantIDs []string
	walletIDs   []string
	inserted    atomic.Int64
)

func main() {
	flag.Parse()
	start := time.Now()

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()

	pool, err := pgxpool.New(ctx, *dbURL)
	if err != nil {
		log.Fatalf("Failed to connect to PostgreSQL: %v", err)
	}
	defer pool.Close()

	log.Println("Connected to PostgreSQL")

	if *clean {
		log.Println("Truncating all tables...")
		_, _ = pool.Exec(ctx, "TRUNCATE payments, wallets, merchants, users CASCADE")
	}

	log.Printf("Target: %d users + %d merchants + %d wallets + %d payments = %d total records",
		*users, *merchants, *users, *payments, *users+*merchants+*users+*payments)

	log.Println("Step 1/4: Generating users...")
	generateUsers(ctx, pool)

	log.Println("Step 2/4: Generating merchants...")
	generateMerchants(ctx, pool)

	log.Println("Step 3/4: Generating wallets...")
	generateWallets(ctx, pool)

	log.Println("Step 4/4: Generating payments...")
	generatePayments(ctx, pool, start)

	elapsed := time.Since(start)
	log.Printf("DONE! %d records inserted in %s (%.0f inserts/sec)",
		inserted.Load(), elapsed.Round(time.Millisecond),
		float64(inserted.Load())/elapsed.Seconds())
}

func generateUsers(ctx context.Context, pool *pgxpool.Pool) {
	rows := make([][]any, 0, *batch)
	total := 0

	for i := 0; i < *users; i++ {
		id := uuid7()
		email := fmt.Sprintf("user%d@example.com", i+1)
		fullName := fmt.Sprintf("User %d", i+1)
		status := "ACTIVE"

		rows = append(rows, []any{id, email, fullName, status})
		userIDs = append(userIDs, id)

		if len(rows) >= *batch || i == *users-1 {
			_, err := pool.CopyFrom(
				ctx,
				pgx.Identifier{"users"},
				[]string{"id", "email", "full_name", "status"},
				pgx.CopyFromRows(rows),
			)
			if err != nil {
				log.Fatalf("Failed to copy users: %v", err)
			}
			total += len(rows)
			inserted.Add(int64(len(rows)))
			log.Printf("  Users: %d/%d", total, *users)
			rows = rows[:0]
		}
	}
}

func generateMerchants(ctx context.Context, pool *pgxpool.Pool) {
	rows := make([][]any, 0, *batch)
	total := 0

	for i := 0; i < *merchants; i++ {
		id := uuid7()
		name := fmt.Sprintf("Merchant-%d", i+1)
		apiKey := fmt.Sprintf("api-key-%d-%s", i+1, randomString(16))

		rows = append(rows, []any{id, name, apiKey})
		merchantIDs = append(merchantIDs, id)

		if len(rows) >= *batch || i == *merchants-1 {
			_, err := pool.CopyFrom(
				ctx,
				pgx.Identifier{"merchants"},
				[]string{"id", "name", "api_key"},
				pgx.CopyFromRows(rows),
			)
			if err != nil {
				log.Fatalf("Failed to copy merchants: %v", err)
			}
			total += len(rows)
			inserted.Add(int64(len(rows)))
			log.Printf("  Merchants: %d/%d", total, *merchants)
			rows = rows[:0]
		}
	}
}

func generateWallets(ctx context.Context, pool *pgxpool.Pool) {
	rows := make([][]any, 0, *batch)
	total := 0

	for i := 0; i < *users; i++ {
		id := uuid7()
		userID := userIDs[i]
		balance := fmt.Sprintf("%.2f", 50000+rand.Float64()*150000)
		currency := "USD"

		rows = append(rows, []any{id, userID, balance, currency})
		walletIDs = append(walletIDs, id)

		if len(rows) >= *batch || i == *users-1 {
			_, err := pool.CopyFrom(
				ctx,
				pgx.Identifier{"wallets"},
				[]string{"id", "user_id", "balance", "currency"},
				pgx.CopyFromRows(rows),
			)
			if err != nil {
				log.Fatalf("Failed to copy wallets: %v", err)
			}
			total += len(rows)
			inserted.Add(int64(len(rows)))
			log.Printf("  Wallets: %d/%d", total, *users)
			rows = rows[:0]
		}
	}
}

func generatePayments(ctx context.Context, pool *pgxpool.Pool, start time.Time) {
	perWorker := *payments / *workers
	remainder := *payments % *workers
	var wg sync.WaitGroup

	go func() {
		ticker := time.NewTicker(3 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				current := inserted.Load() - int64(*users+*merchants+*users)
				elapsed := time.Since(start).Seconds()
				rate := float64(current) / max(elapsed, 0.01)
				remaining := float64(int64(*payments)-current) / max(rate, 1)
				fmt.Printf("\r  Payments: %d/%d (%.0f/sec, ETA: %s)   ",
					current, *payments, rate, time.Duration(remaining)*time.Second)
			}
		}
	}()

	for w := 0; w < *workers; w++ {
		wg.Add(1)
		count := perWorker
		if w == *workers-1 {
			count += remainder
		}
		go paymentWorker(ctx, pool, w, count, &wg)
	}

	wg.Wait()
	fmt.Printf("\r  Payments: %d/%d (100%%)                     \n", *payments, *payments)
}

func paymentWorker(ctx context.Context, pool *pgxpool.Pool, workerID, count int, wg *sync.WaitGroup) {
	defer wg.Done()

	batchRows := make([][]any, 0, *batch)
	localInserted := 0
	rng := rand.New(rand.NewSource(time.Now().UnixNano() + int64(workerID)))
	numUsers := len(userIDs)
	numMerchants := len(merchantIDs)
	now := time.Now()

	for i := 0; i < count; i++ {
		userIdx := rng.Intn(numUsers)
		merchantIdx := rng.Intn(numMerchants)
		status := statuses[rng.Intn(len(statuses))]
		pType := types[rng.Intn(len(types))]
		amount := fmt.Sprintf("%.2f", 1+rng.Float64()*9999)
		createdAt := now.Add(-time.Duration(rng.Int63n(365*24*60*60)) * time.Second)

		batchRows = append(batchRows, []any{
			userIDs[userIdx],
			merchantIDs[merchantIdx],
			walletIDs[userIdx],
			amount,
			pType,
			status,
			createdAt,
		})

		if len(batchRows) >= *batch || i == count-1 {
			_, err := pool.CopyFrom(
				ctx,
				pgx.Identifier{"payments"},
				[]string{"user_id", "merchant_id", "wallet_id", "amount", "type", "status", "created_at"},
				pgx.CopyFromRows(batchRows),
			)
			if err != nil {
				log.Printf("Worker %d: COPY error: %v", workerID, err)
				return
			}
			localInserted += len(batchRows)
			inserted.Add(int64(len(batchRows)))
			batchRows = batchRows[:0]
		}
	}
}

func uuid7() string {
	b := make([]byte, 16)
	rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x70
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

func randomString(n int) string {
	const letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	b := make([]byte, n)
	for i := range b {
		b[i] = letters[rand.Intn(len(letters))]
	}
	return string(b)
}
