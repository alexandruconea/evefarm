import json
import sys
import urllib.request


def components(node):
    for component in node.get("components", []) or []:
        yield component
        yield from components(component)


with open(sys.argv[1], encoding="utf-8") as file:
    bom = json.load(file)
purls = sorted({component["purl"].split("?")[0] for component in components(bom) if component.get("purl")})
if not purls:
    print(f"::error::No libraries found in {sys.argv[1]}")
    sys.exit(1)

request = urllib.request.Request(
    "https://api.osv.dev/v1/querybatch",
    data=json.dumps({"queries": [{"package": {"purl": purl}} for purl in purls]}).encode(),
    headers={"Content-Type": "application/json"},
)
with urllib.request.urlopen(request, timeout=60) as response:
    results = json.load(response)["results"]

vulnerable = [(purl, [vuln["id"] for vuln in result["vulns"]])
              for purl, result in zip(purls, results) if result.get("vulns")]
print(f"Checked {len(purls)} libraries against OSV.dev: {len(vulnerable)} with known vulnerabilities")
for purl, ids in vulnerable:
    print(f"::error::{purl} has known vulnerabilities: {', '.join(ids)}")
sys.exit(1 if vulnerable else 0)
