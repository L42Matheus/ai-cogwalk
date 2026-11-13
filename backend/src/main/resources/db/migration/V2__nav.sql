-- AI-CogWalk-NAV: predição de escolhas de navegação comparada com escolhas humanas.

CREATE TABLE nav_dataset (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    source_url VARCHAR(1000),
    revision VARCHAR(80),
    license VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE nav_state (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES nav_dataset(id) ON DELETE CASCADE,
    state_key VARCHAR(120) NOT NULL,
    variant VARCHAR(40) NOT NULL,
    modality VARCHAR(10) NOT NULL,
    task_key VARCHAR(40) NOT NULL,
    task_text TEXT NOT NULL,
    node_path VARCHAR(200) NOT NULL DEFAULT '',
    options_json TEXT NOT NULL,
    human_counts_json TEXT NOT NULL,
    human_n INT NOT NULL DEFAULT 0,
    screenshot_path VARCHAR(1000),
    viewport VARCHAR(20),
    split VARCHAR(10) NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    UNIQUE (dataset_id, state_key)
);

CREATE TABLE nav_run (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES nav_dataset(id) ON DELETE CASCADE,
    method VARCHAR(20) NOT NULL,
    model VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    error TEXT,
    config_json TEXT,
    summary_json TEXT,
    frozen_hash VARCHAR(64),
    states_total INT NOT NULL DEFAULT 0,
    states_done INT NOT NULL DEFAULT 0,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    llm_calls INT NOT NULL DEFAULT 0,
    llm_millis BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE nav_prediction (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES nav_run(id) ON DELETE CASCADE,
    state_id BIGINT NOT NULL REFERENCES nav_state(id) ON DELETE CASCADE,
    distribution_json TEXT NOT NULL,
    samples_json TEXT,
    detail_json TEXT,
    prompt_system TEXT,
    prompt_user TEXT,
    prompt_sha VARCHAR(64),
    metrics_json TEXT,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    cache_hits INT NOT NULL DEFAULT 0,
    error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (run_id, state_id)
);
