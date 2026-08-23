def log = new File(basedir, 'build.log').text
assert log.contains('cannot attach more than one API without an alias')
return true
