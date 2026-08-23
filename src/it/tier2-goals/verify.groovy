def log = new File(basedir, 'build.log').text
assert log.contains('Statistics for api/openapi.yaml:')
assert log.contains('Statistics for api/orders.yaml:')
assert log.contains('Operations: 2')
assert log.contains('Score for api/openapi.yaml:')
assert log.contains('Score for api/orders.yaml:')
assert log.contains('Agent Readiness:')
assert log.contains('scores of 2 APIs meet the required minimum of 10')
assert log.contains('Joined 2 API descriptions (api/openapi.yaml, api/orders.yaml) into target/generated-resources/openapi/joined.yaml')
assert log.contains('Split api/orders.yaml into target/split')
assert log.contains('BUILD SUCCESS')

def joined = new File(basedir, 'target/generated-resources/openapi/joined.yaml').text
assert joined.contains('/pets:')
assert joined.contains('/orders:')
assert joined.contains('Petstore_pets')

assert new File(basedir, 'target/split/openapi.yaml').exists()
assert new File(basedir, 'target/split/paths/orders.yaml').exists()
assert new File(basedir, 'target/split/components/schemas/Order.yaml').exists()
return true
