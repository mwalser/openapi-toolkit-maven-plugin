// styled-components' server build imports Node streams for interleaveWithNodeStream, which build-docs never calls.
export class Readable {}
export class Transform {}
export default { Readable, Transform };
