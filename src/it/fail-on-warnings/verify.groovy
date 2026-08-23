def log = new File(basedir, 'build.log').text
assert log.contains('OpenAPI lint failed with')
assert log.contains('(failOnWarnings=true)')
return true
