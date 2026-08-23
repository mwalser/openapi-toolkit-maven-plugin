import java.util.zip.ZipFile

def log = new File(basedir, 'build.log').text
assert log.contains('Skipping (openapi.skip=true)')
assert new File(basedir, 'bundled/generated.yaml').isFile()

// addResource with outputFile must add only the bundle, not the whole module directory
new ZipFile(new File(basedir, 'bundled/target/bundled-1.0-SNAPSHOT.jar')).withCloseable { jar ->
  assert jar.getEntry('generated.yaml') != null
  assert jar.getEntry('openapi.yaml') == null
  assert jar.getEntry('pom.xml') == null
}
return true
