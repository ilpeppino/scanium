import prismaPkg from "@prisma/client";

const { PrismaClient } = prismaPkg as unknown as { PrismaClient: new (...args: any[]) => any };

// Create a single PrismaClient instance for the process
export const prisma = new PrismaClient();
