def log = new File(basedir, 'build.log').text
assert log.contains("ENETDOWN: Maven is offline (-o), remote references are not fetched, fetch 'https://example.invalid/schema.yaml'")
return true
