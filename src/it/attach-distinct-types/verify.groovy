def log = new File(basedir, 'build.log').text
assert log.contains('(type=yaml, classifier=openapi)')
assert log.contains('(type=json, classifier=openapi)')
assert log.count('Attached ') == 2
assert log.contains('BUILD SUCCESS')
return true
