CREATE TABLE IF NOT EXISTS listings (
  id INT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  price INT NULL,
  location VARCHAR(100),
  description TEXT,
  phone VARCHAR(50),
  source VARCHAR(20),
  risk_level VARCHAR(20),
  risk_tags VARCHAR(255),
  eval_group VARCHAR(20),
  multi_tier_pricing TINYINT DEFAULT 0,
  label_note TEXT,
  INDEX idx_phone (phone)
);

CREATE TABLE IF NOT EXISTS case_vectors (
  listing_id INT PRIMARY KEY,
  embedded_text TEXT,
  vector_json MEDIUMTEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS scam_rule (
  id INT PRIMARY KEY AUTO_INCREMENT,
  rule_type VARCHAR(50) NOT NULL UNIQUE,
  pattern VARCHAR(500),
  weight DOUBLE DEFAULT 0.0,
  note TEXT,
  enabled TINYINT DEFAULT 1
);
