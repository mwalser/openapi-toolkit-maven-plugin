def log = new File(basedir, 'build.log').text
assert log.contains('[ERROR] openapi.yaml:')
assert log.contains('security-defined')
assert log.contains('1 API description validated: 2 errors, 3 warnings')
assert log.contains('Lint failed with 2 errors.')
return true
