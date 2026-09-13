def log = new File(basedir, 'build.log').text
assert log.contains('would overwrite artifact (type=yaml, classifier=openapi)')
assert log.count('Attached ') == 1
return true
