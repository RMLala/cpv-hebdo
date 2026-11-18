CREATE TABLE IF NOT EXISTS products (
  product_id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('active','inactive')),
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS licenses (
  id TEXT PRIMARY KEY,
  product_id TEXT NOT NULL REFERENCES products(product_id),
  customer_name TEXT NOT NULL,
  customer_email TEXT NOT NULL DEFAULT '',
  code_hash TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL CHECK(status IN ('active','revoked')),
  paid_confirmed INTEGER NOT NULL DEFAULT 0 CHECK(paid_confirmed IN (0,1)),
  plan TEXT NOT NULL CHECK(plan IN ('lifetime','annual')),
  max_devices INTEGER NOT NULL DEFAULT 1 CHECK(max_devices BETWEEN 1 AND 10),
  expires_at TEXT,
  payment_reference TEXT NOT NULL,
  created_at TEXT NOT NULL,
  revoked_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_licenses_product_created ON licenses(product_id, created_at DESC);
CREATE TABLE IF NOT EXISTS activations (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  license_id TEXT NOT NULL REFERENCES licenses(id),
  installation_hash TEXT NOT NULL,
  activated_at TEXT NOT NULL,
  last_seen TEXT NOT NULL,
  UNIQUE(license_id, installation_hash)
);
CREATE TABLE IF NOT EXISTS activation_attempts (
  subject_hash TEXT NOT NULL,
  bucket INTEGER NOT NULL,
  failures INTEGER NOT NULL,
  PRIMARY KEY(subject_hash, bucket)
);
CREATE TABLE IF NOT EXISTS usage_daily (
  day TEXT PRIMARY KEY,
  activation_requests INTEGER NOT NULL DEFAULT 0
);
INSERT OR IGNORE INTO products(product_id,name,status,created_at)
VALUES('PROMPTI-V1','Prompti','active',CURRENT_TIMESTAMP);

ALTER TABLE usage_daily ADD COLUMN status_requests INTEGER NOT NULL DEFAULT 0;

ALTER TABLE usage_daily ADD COLUMN trial_requests INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS trials (
  product_id TEXT NOT NULL REFERENCES products(product_id),
  installation_hash TEXT NOT NULL,
  started_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  PRIMARY KEY(product_id, installation_hash)
);

CREATE INDEX IF NOT EXISTS idx_trials_expiry ON trials(expires_at);

ALTER TABLE licenses ADD COLUMN license_type TEXT NOT NULL DEFAULT 'legacy';
UPDATE licenses SET license_type = plan WHERE license_type = 'legacy';
CREATE INDEX IF NOT EXISTS idx_licenses_expiration ON licenses(status, expires_at);

-- Preserve license and activation history while supporting user-requested deletion from admin lists.
CREATE TABLE IF NOT EXISTS deleted_products (
  product_id TEXT PRIMARY KEY REFERENCES products(product_id),
  deleted_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS deleted_licenses (
  license_id TEXT PRIMARY KEY REFERENCES licenses(id),
  deleted_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS cloud_snapshots (
  access_id TEXT NOT NULL,
  product_id TEXT NOT NULL,
  resource TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1,
  payload TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (access_id, product_id, resource)
);
CREATE INDEX IF NOT EXISTS idx_cloud_snapshots_updated ON cloud_snapshots(updated_at);

-- Non-destructive, configurable technical defaults; not commercial plan definitions.
ALTER TABLE licenses ADD COLUMN cloud_enabled INTEGER NOT NULL DEFAULT 1 CHECK(cloud_enabled IN (0,1));
ALTER TABLE licenses ADD COLUMN cloud_quota_bytes INTEGER NOT NULL DEFAULT 104857600 CHECK(cloud_quota_bytes BETWEEN 1024 AND 1073741824);
ALTER TABLE licenses ADD COLUMN cloud_requests_day INTEGER NOT NULL DEFAULT 10000 CHECK(cloud_requests_day BETWEEN 1 AND 100000);
CREATE TABLE IF NOT EXISTS cloud_usage_daily (
 access_id TEXT NOT NULL, product_id TEXT NOT NULL, day TEXT NOT NULL,
 requests INTEGER NOT NULL DEFAULT 0,
 PRIMARY KEY(access_id,product_id,day)
);
