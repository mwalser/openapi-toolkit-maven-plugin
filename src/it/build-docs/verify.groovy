def log = new File(basedir, 'build.log').text.replace('\\', '/') // paths are displayed with the host separator
assert log.contains("Created documentation for api/openapi.yaml using configuration for api 'petstore' at target/generated-resources/redoc/petstore.html (")
assert log.contains('Added petstore.html in target/generated-resources/redoc as resources')
assert log.contains('BUILD SUCCESS')

def page = new File(basedir, 'target/generated-resources/redoc/petstore.html').text
assert page.contains('<title>Petstore reference</title>')
assert page.contains('<div id="redoc">')
assert page.contains('Redoc.hydrate(__redoc_state, container);')
assert page.contains('"hideDownloadButton":true') // from redocly.yaml
assert page.contains('"expandResponses":"all"')   // from the POM
assert page.contains('List pets')

// addResource=true: the page ends up at the root of the jar
def jar = new java.util.jar.JarFile(new File(basedir, 'target/build-docs-1.0-SNAPSHOT.jar'))
assert jar.getEntry('petstore.html') != null
jar.close()
return true
