CREATE TABLE projects (id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL, name VARCHAR(255) NOT NULL, owner VARCHAR(255) NOT NULL);
CREATE INDEX projects_owner ON projects(owner);
CREATE TABLE targets (id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL, project_id UUID NOT NULL REFERENCES projects(id), name VARCHAR(255) NOT NULL, base_url VARCHAR(255) NOT NULL, environment VARCHAR(255) NOT NULL CHECK(environment='LOCAL_DEMO'), authorized BOOLEAN NOT NULL CHECK(authorized));
CREATE TABLE scenarios (id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL, target_id UUID NOT NULL REFERENCES targets(id), plan TEXT NOT NULL);
CREATE TABLE executions (id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL, scenario_id UUID NOT NULL REFERENCES scenarios(id), status VARCHAR(255) NOT NULL CHECK(status IN ('QUEUED','RUNNING','PASSED','FAILED','INCONCLUSIVE','CANCELLED','TIMED_OUT')), defect VARCHAR(255) NOT NULL, cancel_requested BOOLEAN NOT NULL DEFAULT false, started_at TIMESTAMP WITH TIME ZONE, ended_at TIMESTAMP WITH TIME ZONE, result TEXT);
CREATE INDEX execution_queue ON executions(status,created_at);
CREATE INDEX executions_scenario ON executions(scenario_id);
CREATE TABLE discoveries (id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL, target_id UUID NOT NULL REFERENCES targets(id), status VARCHAR(255) NOT NULL, model TEXT);
CREATE TABLE findings (id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL, execution_id UUID NOT NULL REFERENCES executions(id), severity VARCHAR(255) NOT NULL, confidence VARCHAR(255) NOT NULL, title VARCHAR(255) NOT NULL, detail TEXT NOT NULL);
CREATE INDEX findings_execution ON findings(execution_id);
CREATE INDEX findings_severity ON findings(severity,confidence);
CREATE TABLE reports (id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL, execution_id UUID NOT NULL REFERENCES executions(id), document TEXT NOT NULL);
CREATE TABLE audit_logs (id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL, actor VARCHAR(255) NOT NULL, action VARCHAR(255) NOT NULL, resource_id UUID NOT NULL);
CREATE INDEX audit_resource ON audit_logs(resource_id,created_at);
CREATE INDEX targets_project ON targets(project_id);
CREATE INDEX scenarios_target ON scenarios(target_id);
CREATE INDEX discoveries_queue ON discoveries(status,created_at);

