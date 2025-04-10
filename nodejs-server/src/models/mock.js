const { v4: uuidv4 } = require('uuid');
const bcrypt = require('bcryptjs');

// In-memory data stores
const users = [];
const messages = [];
const refreshTokens = [];
const userKeys = [];
const sessions = [];
const oneTimePrekeys = [];
const contacts = [];  // New array for contacts

// Mock User model
class User {
  static async findAll(options = {}) {
    if (options.where) {
      return users.filter(user => 
        Object.keys(options.where).every(key => 
          user[key] === options.where[key]
        )
      );
    }
    return [...users];
  }
  
  static async findOne(options = {}) {
    if (options.where) {
      return users.find(user => 
        Object.keys(options.where).every(key => 
          user[key] === options.where[key]
        )
      ) || null;
    }
    return users[0] || null;
  }
  
  static async findByPk(id) {
    return users.find(user => user.id === id) || null;
  }
  
  static async create(data) {
    const newUser = {
      id: data.id || uuidv4(),
      createdAt: new Date(),
      updatedAt: new Date(),
      lastActiveAt: new Date(),
      isActive: true,
      ...data,
      // Add toProfile method to user instances
      toProfile: function() {
        return {
          id: this.id,
          username: this.username,
          displayName: this.displayName,
          isActive: this.isActive,
          lastActiveAt: this.lastActiveAt
        };
      }
    };
    users.push(newUser);
    return newUser;
  }
  
  static async update(updates, options) {
    let updatedCount = 0;
    
    if (options.where) {
      users.forEach((user, index) => {
        if (Object.keys(options.where).every(key => user[key] === options.where[key])) {
          // Preserve the toProfile method
          const toProfile = user.toProfile;
          users[index] = { ...user, ...updates, updatedAt: new Date(), toProfile };
          updatedCount++;
        }
      });
    } else if (options.individualHooks && options.returning) {
      // For single instance update
      const user = users.find(u => u.id === updates.id);
      if (user) {
        const toProfile = user.toProfile;
        Object.assign(user, updates, { updatedAt: new Date(), toProfile });
        updatedCount = 1;
        return [updatedCount, [user]];
      }
    }
    
    return options.individualHooks && options.returning ? [updatedCount, users.filter(u => 
      Object.keys(options.where).every(key => u[key] === options.where[key])
    )] : updatedCount;
  }
  
  static async destroy(options) {
    const initialLength = users.length;
    
    if (options.where) {
      const indicesToRemove = [];
      users.forEach((user, index) => {
        if (Object.keys(options.where).every(key => user[key] === options.where[key])) {
          indicesToRemove.push(index);
        }
      });
      
      // Remove in reverse order to avoid index issues
      for (let i = indicesToRemove.length - 1; i >= 0; i--) {
        users.splice(indicesToRemove[i], 1);
      }
    }
    
    return initialLength - users.length;
  }
}

// Mock Message model
class Message {
  static async findAll(options = {}) {
    if (options.where) {
      return messages.filter(message => 
        Object.keys(options.where).every(key => 
          message[key] === options.where[key]
        )
      );
    }
    return [...messages];
  }
  
  static async findOne(options = {}) {
    if (options.where) {
      return messages.find(message => 
        Object.keys(options.where).every(key => 
          message[key] === options.where[key]
        )
      ) || null;
    }
    return messages[0] || null;
  }
  
  static async findByPk(id) {
    return messages.find(message => message.id === id) || null;
  }
  
  static async create(data) {
    const newMessage = {
      id: data.id || uuidv4(),
      createdAt: new Date(),
      updatedAt: new Date(),
      sent: true,
      received: false,
      read: false,
      deleted: false,
      ...data
    };
    messages.push(newMessage);
    return newMessage;
  }
  
  static async update(updates, options) {
    let updatedCount = 0;
    
    if (options.where) {
      messages.forEach((message, index) => {
        if (Object.keys(options.where).every(key => message[key] === options.where[key])) {
          messages[index] = { ...message, ...updates, updatedAt: new Date() };
          updatedCount++;
        }
      });
    }
    
    return updatedCount;
  }
  
  static async destroy(options) {
    const initialLength = messages.length;
    
    if (options.where) {
      const indicesToRemove = [];
      messages.forEach((message, index) => {
        if (Object.keys(options.where).every(key => message[key] === options.where[key])) {
          indicesToRemove.push(index);
        }
      });
      
      // Remove in reverse order to avoid index issues
      for (let i = indicesToRemove.length - 1; i >= 0; i--) {
        messages.splice(indicesToRemove[i], 1);
      }
    }
    
    return initialLength - messages.length;
  }
}

// Mock RefreshToken model
class RefreshToken {
  static async findOne(options = {}) {
    if (options.where) {
      return refreshTokens.find(token => 
        Object.keys(options.where).every(key => 
          token[key] === options.where[key]
        )
      ) || null;
    }
    return refreshTokens[0] || null;
  }
  
  static async create(data) {
    const newToken = {
      id: uuidv4(),
      token: data.token,
      userId: data.userId,
      createdAt: new Date(),
      updatedAt: new Date(),
      expiresAt: data.expiresAt || new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days
      revoked: false,
      revokedAt: null
    };
    refreshTokens.push(newToken);
    return newToken;
  }
  
  static async update(updates, options) {
    let updatedCount = 0;
    
    if (options.where) {
      refreshTokens.forEach((token, index) => {
        if (Object.keys(options.where).every(key => token[key] === options.where[key])) {
          refreshTokens[index] = { ...token, ...updates, updatedAt: new Date() };
          updatedCount++;
        }
      });
    }
    
    return updatedCount;
  }
}

// Mock UserKey model for crypto
class UserKey {
  static async findOne(options = {}) {
    if (options.where) {
      return userKeys.find(key => 
        Object.keys(options.where).every(k => 
          key[k] === options.where[k]
        )
      ) || null;
    }
    return userKeys[0] || null;
  }
  
  static async create(data) {
    const newKey = {
      id: uuidv4(),
      createdAt: new Date(),
      updatedAt: new Date(),
      ...data
    };
    userKeys.push(newKey);
    return newKey;
  }
  
  static async update(updates, options) {
    let updatedCount = 0;
    
    if (options.where) {
      userKeys.forEach((key, index) => {
        if (Object.keys(options.where).every(k => key[k] === options.where[k])) {
          userKeys[index] = { ...key, ...updates, updatedAt: new Date() };
          updatedCount++;
        }
      });
    }
    
    return updatedCount;
  }
  
  static async destroy(options) {
    const initialLength = userKeys.length;
    
    if (options.where) {
      const indicesToRemove = [];
      userKeys.forEach((key, index) => {
        if (Object.keys(options.where).every(k => key[k] === options.where[k])) {
          indicesToRemove.push(index);
        }
      });
      
      // Remove in reverse order to avoid index issues
      for (let i = indicesToRemove.length - 1; i >= 0; i--) {
        userKeys.splice(indicesToRemove[i], 1);
      }
    }
    
    return initialLength - userKeys.length;
  }
}

// Add Contact model
class Contact {
  static async findAll(options = {}) {
    if (options.where) {
      return contacts.filter(contact => 
        Object.keys(options.where).every(key => 
          contact[key] === options.where[key]
        )
      );
    }
    return [...contacts];
  }
  
  static async findOne(options = {}) {
    if (options.where) {
      return contacts.find(contact => 
        Object.keys(options.where).every(key => 
          contact[key] === options.where[key]
        )
      ) || null;
    }
    return contacts[0] || null;
  }
  
  static async create(data) {
    const newContact = {
      id: uuidv4(),
      createdAt: new Date(),
      updatedAt: new Date(),
      ...data
    };
    contacts.push(newContact);
    return newContact;
  }
}

// Initialize with some seed data
const init = async () => {
  // Create admin user
  if (users.length === 0) {
    const hashedPassword = await bcrypt.hash('admin123', 10);
    await User.create({
      username: 'admin',
      email: 'admin@example.com',
      passwordHash: hashedPassword,
      displayName: 'Admin User',
      isActive: true
    });
    
    // Create a regular user
    const userPassword = await bcrypt.hash('user123', 10);
    await User.create({
      username: 'user',
      email: 'user@example.com',
      passwordHash: userPassword,
      displayName: 'Regular User',
      isActive: true
    });
    
    console.log('Mock database initialized with seed data');
  }
};

// Initialize mock data
init();

// Mock sequelize
const mockSequelize = {
  sync: () => Promise.resolve(),
  close: () => Promise.resolve()
};

// Export mock models
module.exports = {
  User,
  Message,
  RefreshToken,
  UserKey,
  Session: {
    findOne: () => Promise.resolve(null),
    create: (data) => Promise.resolve({ id: uuidv4(), ...data }),
    update: () => Promise.resolve(1)
  },
  OneTimePreKey: {
    findOne: () => Promise.resolve(null),
    create: (data) => Promise.resolve({ id: uuidv4(), ...data }),
    update: () => Promise.resolve(1)
  },
  Contact,
  sequelize: mockSequelize,
  Sequelize: {
    Op: {
      and: Symbol('and'),
      or: Symbol('or'),
      eq: Symbol('eq'),
      ne: Symbol('ne'),
      gt: Symbol('gt'),
      lt: Symbol('lt'),
      gte: Symbol('gte'),
      lte: Symbol('lte'),
      like: Symbol('like'),
      notLike: Symbol('notLike'),
      in: Symbol('in'),
      notIn: Symbol('notIn')
    }
  }
}; 