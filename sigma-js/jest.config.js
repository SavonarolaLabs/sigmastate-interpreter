/** @type {import("jest").Config} */
const config = {
	transform: {}, // reduce non-cached test time by about 20x by disabling babel code transformation
	moduleDirectories: ["<rootDir>/node_modules"],
	moduleNameMapper: {
		"sigmastate-js/main": "<rootDir>/node_modules/sigmastate-js/dist/main.js",
	},
};

module.exports = config;
