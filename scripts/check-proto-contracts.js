const fs = require("fs");
const path = require("path");

const repoRoot = __dirname ? path.resolve(__dirname, "..") : process.cwd();
const protoRoot = path.join(repoRoot, "api", "proto");
const violations = [];

function walk(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...walk(fullPath));
    } else if (entry.isFile() && entry.name.endsWith(".proto")) {
      files.push(fullPath);
    }
  }
  return files.sort();
}

function addViolation(filePath, message) {
  const relative = path.relative(repoRoot, filePath);
  violations.push(`${relative}: ${message}`);
}

function checkProtoFile(filePath) {
  const content = fs.readFileSync(filePath, "utf8");

  if (!/^\s*syntax\s*=\s*"proto3";/m.test(content)) {
    addViolation(filePath, 'missing `syntax = "proto3";`');
  }

  if (!/^\s*package\s+[a-z0-9_.]+\s*;/m.test(content)) {
    addViolation(filePath, "missing package declaration");
  }

  const hasService = /^\s*service\s+\w+\s*\{/m.test(content);
  if (hasService && !/import\s+"google\/api\/annotations\.proto";/.test(content)) {
    addViolation(filePath, "service definitions must import google/api/annotations.proto");
  }

  const rpcBlocks = content.match(/rpc\s+\w+\s*\([^)]*\)\s+returns\s+\([^)]*\)\s*\{[\s\S]*?\}/g) || [];
  for (const block of rpcBlocks) {
    if (!/option\s+\(google\.api\.http\)\s*=/.test(block)) {
      addViolation(filePath, "each rpc must declare a google.api.http mapping");
    }
  }

  const timestampFieldPattern =
    /^\s*(string|int32|int64|uint32|uint64|fixed32|fixed64|sfixed32|sfixed64|sint32|sint64)\s+([a-z0-9_]*(?:_at|_time|_timestamp))\s*=/gm;
  let match;
  while ((match = timestampFieldPattern.exec(content)) !== null) {
    addViolation(
      filePath,
      `time field \`${match[2]}\` must use google.protobuf.Timestamp instead of \`${match[1]}\``
    );
  }
}

if (!fs.existsSync(protoRoot)) {
  console.error(`Missing proto root: ${protoRoot}`);
  process.exit(1);
}

const protoFiles = walk(protoRoot);
if (protoFiles.length === 0) {
  console.error("No proto files found under api/proto");
  process.exit(1);
}

for (const filePath of protoFiles) {
  checkProtoFile(filePath);
}

if (violations.length > 0) {
  console.error("Proto contract checks failed:");
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log(`Proto contract checks passed for ${protoFiles.length} file(s).`);
