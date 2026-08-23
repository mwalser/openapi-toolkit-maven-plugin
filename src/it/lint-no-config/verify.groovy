def log = new File(basedir, 'build.log').text
assert log.contains("No Redocly configuration found") == false  // extends given -> not the default-config message
assert log.contains('Validating openapi.json (')
assert log.contains('API description validated:')
assert log.contains('BUILD SUCCESS')
return true
