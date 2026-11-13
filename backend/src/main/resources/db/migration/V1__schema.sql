CREATE TABLE project (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    base_url VARCHAR(1000) NOT NULL,
    allowed_hosts VARCHAR(1000) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE task_def (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    goal TEXT NOT NULL,
    user_profile TEXT NOT NULL,
    expected_knowledge TEXT,
    start_url VARCHAR(1000) NOT NULL,
    steps_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE run (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES task_def(id) ON DELETE CASCADE,
    exp_condition VARCHAR(8) NOT NULL,
    evaluator VARCHAR(32) NOT NULL,
    model VARCHAR(100),
    effort VARCHAR(20),
    prompt_version VARCHAR(40),
    status VARCHAR(20) NOT NULL,
    error TEXT,
    source_run_id BIGINT,
    config_json TEXT,
    prompt_strategy VARCHAR(20) NOT NULL DEFAULT 'ZERO_SHOT',
    examples_json TEXT,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    llm_millis BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE run_step (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES run(id) ON DELETE CASCADE,
    step_index INT NOT NULL,
    url TEXT,
    title TEXT,
    action_json TEXT,
    target_element_id VARCHAR(20),
    target_candidates INT NOT NULL DEFAULT 0,
    screenshot_path VARCHAR(1000),
    fullpage_path VARCHAR(1000),
    html_path VARCHAR(1000),
    elements_json TEXT,
    headings_json TEXT,
    captured_at TIMESTAMP WITH TIME ZONE,
    assessment_json TEXT,
    prompt_system TEXT,
    prompt_user TEXT,
    prompt_sha VARCHAR(64),
    raw_response TEXT,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    cache_hit BOOLEAN NOT NULL DEFAULT FALSE,
    eval_error TEXT
);
CREATE INDEX idx_run_step_run ON run_step(run_id, step_index);

CREATE TABLE alert (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES run(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    step_index INT NOT NULL,
    criterion VARCHAR(4) NOT NULL,
    visual_evidence TEXT,
    diagnosis TEXT,
    cited_elements VARCHAR(500),
    recommendation TEXT,
    model_confidence DOUBLE PRECISION,
    state VARCHAR(20) NOT NULL,
    verification_note TEXT,
    review_decision VARCHAR(20) NOT NULL,
    review_note TEXT,
    reviewer VARCHAR(120),
    review_seconds INT,
    reviewed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_alert_run ON alert(run_id);
