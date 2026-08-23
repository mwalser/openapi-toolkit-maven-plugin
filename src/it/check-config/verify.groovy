def log = new File(basedir, 'build.log').text
assert log.contains('[ERROR] redocly.yaml:')
assert log.contains('info-license')
assert log.contains('1 error, 0 warnings. Fix the configuration or set lintConfig=off.')
return true
